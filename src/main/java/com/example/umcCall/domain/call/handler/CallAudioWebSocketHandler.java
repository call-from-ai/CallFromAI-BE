package com.example.umcCall.domain.call.handler;

import com.example.umcCall.domain.call.client.ClovaSpeechClient;
import com.example.umcCall.domain.call.client.ClovaSpeechProperties;
import com.example.umcCall.domain.ai.dto.AiChatHistoryItem;
import com.example.umcCall.domain.ai.dto.AiChatResponse;
import com.example.umcCall.domain.call.client.ClovaVoiceClient;
import com.example.umcCall.domain.call.dto.NestRecognizeResult;
import com.example.umcCall.domain.call.service.CallConversationService;
import com.example.umcCall.domain.call.service.CallService;
import com.example.umcCall.domain.call.ticket.WsTicket;
import com.example.umcCall.domain.call.ticket.WsTicketHandshakeInterceptor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import com.nbp.cdncp.nest.grpc.proto.v1.NestConfig;
import com.nbp.cdncp.nest.grpc.proto.v1.NestData;
import com.nbp.cdncp.nest.grpc.proto.v1.NestRequest;
import com.nbp.cdncp.nest.grpc.proto.v1.NestResponse;
import com.nbp.cdncp.nest.grpc.proto.v1.RequestType;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

/**
 * 통화 오디오 양방향 채널 핸들러. 통화마다 분리해 처리한다.
 * 업스트림은 바이너리 프레임(raw PCM 16kHz/모노/16-bit)을 CLOVA STT로 중계하고,
 * 다운스트림은 STT final을 AI(chat)로 넘겨 응답 대사를 TTS로 합성해 wav를 그대로 내려보낸다. 제어 신호는 텍스트(JSON) 프레임.
 * <p>final은 AI로, partial은 클라이언트 자막 전용(AI로 보내지 않는다).
 */
@Slf4j
@Component
public class CallAudioWebSocketHandler extends AbstractWebSocketHandler {

    /** AI 응답 화자. 캐릭터별 음성 매핑은 후순위 — 지금은 고정값(캐릭터 음성이 들어올 자리다). */
    private static final String AI_SPEAKER = "nara";

    private final ClovaSpeechClient clovaSpeechClient;
    private final ClovaVoiceClient clovaVoiceClient;
    private final CallConversationService callConversationService;
    private final CallService callService;
    private final ObjectMapper objectMapper;

    /** CLOVA 인식 설정(JSON). 한국어 + 침묵(gap) 기반 턴 끝 감지. gapThreshold는 yml에서 온다. */
    private final String configJson;

    /** WS 세션 ID → 진행 중인 통화. WS 수신 · gRPC 콜백 · 워커 세 스레드가 만나는 지점이다. */
    private final ConcurrentHashMap<String, ActiveCall> activeCalls = new ConcurrentHashMap<>();

    public CallAudioWebSocketHandler(ClovaSpeechClient clovaSpeechClient,
                                     ClovaVoiceClient clovaVoiceClient,
                                     CallConversationService callConversationService,
                                     CallService callService,
                                     ClovaSpeechProperties speechProperties,
                                     ObjectMapper objectMapper) {
        this.clovaSpeechClient = clovaSpeechClient;
        this.clovaVoiceClient = clovaVoiceClient;
        this.callConversationService = callConversationService;
        this.callService = callService;
        this.objectMapper = objectMapper;
        this.configJson = buildConfigJson(objectMapper, speechProperties.gapThresholdMs());
        log.info("[Clova] recognize CONFIG = {}", configJson);
    }

    /**
     * 발화가 이만큼(ms) 이어지면 gap 없이도 결과를 확정하는 상한. 길이 토막을 막으려 크게 둔다.
     * ⚠ 미설정(0)이면 CLOVA가 최소 길이로 즉시 확정해 ~0.4s마다 durationThreshold로 조각난다 — 반드시 명시.
     */
    private static final int MAX_SEGMENT_MS = 20000;

    /**
     * CLOVA recognize CONFIG를 만든다. 턴 끝 = 침묵(gap): {@code gapThreshold} ms 침묵하면 final.
     * {@code usePeriodEpd=false}로 문장부호 확정을 꺼 다문장 턴을 안 쪼갠다. ({@code durationThreshold}는 {@link #MAX_SEGMENT_MS})
     */
    private static String buildConfigJson(ObjectMapper objectMapper, int gapThresholdMs) {
        Map<String, Object> config = Map.of(
                "transcription", Map.of("language", "ko"),
                "semanticEpd", Map.of(
                        "gapThreshold", gapThresholdMs,
                        "durationThreshold", MAX_SEGMENT_MS,
                        "usePeriodEpd", false,
                        "skipEmptyText", true));
        try {
            return objectMapper.writeValueAsString(config);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("CLOVA CONFIG JSON 직렬화 실패", e);
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String sessionId = session.getId();

        // 핸드셰이크 인터셉터가 검증해 실어둔 신원. 정상 경로엔 항상 있다(없으면 방어적으로 종료).
        WsTicket ticket = (WsTicket) session.getAttributes()
                .get(WsTicketHandshakeInterceptor.WS_TICKET_ATTRIBUTE);
        if (ticket == null) {
            log.error("[Call] 세션에 wsTicket 신원이 없음 → WebSocket 종료. session={}", sessionId);
            terminateCall(session, CloseStatus.SERVER_ERROR);
            return;
        }

        try {
            StreamObserver<NestRequest> requestObserver =
                    clovaSpeechClient.openRecognizeStream(new ClovaResponseObserver(session));
            SttStream stream = new SttStream(requestObserver);
            // 워커는 스트림 개설 성공 뒤에 만든다(실패하면 정리할 워커도 없도록).
            // 등록은 CONFIG 전에 — onNext가 처음 뜰 땐 이미 맵에 있어야 한다.
            activeCalls.put(sessionId,
                    new ActiveCall(stream, Executors.newSingleThreadExecutor(), ticket, new ArrayList<>()));

            // CONFIG 1회 → 이후 오디오는 DATA로. (CLOVA recognize 스트림 규약)
            stream.send(NestRequest.newBuilder()
                    .setType(RequestType.CONFIG)
                    .setConfig(NestConfig.newBuilder().setConfig(configJson).build())
                    .build());

            // DIALING → IN_PROGRESS. 상태 persist 실패로 통화를 끊지는 않는다(로그만) — chat() 턴 폐기 정책과 결이 같다.
            try {
                callService.connect(ticket.callId());
            } catch (RuntimeException e) {
                log.error("[Call] 통화 연결 상태 저장 실패(통화는 유지). session={}, callId={}",
                        sessionId, ticket.callId(), e);
            }

            log.info("[Call] WebSocket 연결 · CLOVA 스트림 개설. session={}, callId={}",
                    sessionId, ticket.callId());
        } catch (RuntimeException e) {
            // 개설 자체의 실패만 여기로 온다. 비동기 연결 실패는 ClovaResponseObserver.onError로 간다.
            log.error("[Call] CLOVA 스트림 개설 실패 → WebSocket 종료. session={}", sessionId, e);
            terminateCall(session, CloseStatus.SERVER_ERROR);
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        ActiveCall call = activeCalls.get(session.getId());
        if (call == null) {
            log.warn("[Call] CLOVA 스트림이 없어 오디오를 버림. session={}", session.getId());
            return;
        }
        SttStream stream = call.stream();
        ByteBuffer payload = message.getPayload();
        if (!payload.hasRemaining()) {
            log.debug("[Call] 빈 오디오 프레임 무시. session={}", session.getId());
            return;
        }
        byte[] chunk = new byte[payload.remaining()];
        payload.get(chunk);

        try {
            stream.send(NestRequest.newBuilder()
                    .setType(RequestType.DATA)
                    .setData(NestData.newBuilder().setChunk(ByteString.copyFrom(chunk)).build())
                    .build());
            log.debug("[Call] 오디오 {} bytes 중계. session={}", chunk.length, session.getId());
        } catch (RuntimeException e) {
            log.error("[Call] 오디오 중계 실패 → WebSocket 종료. session={}", session.getId(), e);
            terminateCall(session, CloseStatus.SERVER_ERROR);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 제어 신호(통화 시작/끝). 파싱/상태머신은 후순위 — 지금은 로그만.
        log.info("[Call] 제어 메시지 수신. session={}, payload={}", session.getId(), message.getPayload());
    }

    /**
     * final 발화를 AI로 넘겨 응답 대사를 TTS로 합성해 다운스트림으로 돌려보낸다.
     * 호출자(gRPC 콜백)를 잡아두지 않도록 통화 워커에 넘기고 즉시 반환한다.
     * <p>chat()은 무거운 REST라 워커에서 비동기 실행하며, 워커 단일 스레드가 턴 순서를 보장한다.
     * 전사 DB 저장(후순위)이 붙으면 {@link CallConversationService} 안에 들어간다.
     */
    private void submitChat(WebSocketSession session, String text) {
        String sessionId = session.getId();
        if (text == null || text.isBlank()) {
            log.debug("[Call] 빈 final → 생략. session={}", sessionId);
            return;
        }
        ActiveCall call = activeCalls.get(sessionId);
        if (call == null) {
            log.debug("[Call] 통화가 없어 발화를 버림. session={}", sessionId);
            return;
        }
        WsTicket ticket = call.ticket();
        List<AiChatHistoryItem> history = call.history();
        try {
            call.worker().execute(() -> {
                try {
                    // 이벤트 로그 방식: 발화가 '실제로 일어난 순간'에 독립적으로 append한다(user/ai 짝 아님).
                    // 워커 단일 스레드가 유일한 writer라 로그 접근은 스레드 confine(동기화 불필요).

                    // STT final 확정 → 사용자 발화를 먼저 남긴다. AI 응답 성공 여부와 무관한 사실이다.
                    // (respond가 실패해도 남는다 — 연속 user는 이 모델에서 정상.) role은 계약대로 소문자.
                    history.add(new AiChatHistoryItem("user", text, LocalDateTime.now()));

                    // respond는 로그의 마지막(방금 넣은 user)을 이번 message로, 그 앞을 이전 턴으로 파생한다.
                    AiChatResponse response = callConversationService.respond(
                            ticket.characterId(), ticket.relationshipId(), history);
                    String reply = response.reply();

                    // AI가 말할 게 없으면(빈 응답) TTS를 건너뛴다. user 로그는 이미 남았고 assistant만 안 남는다
                    // = "사용자는 말했고 AI는 아무 말 안 함"이라 이벤트 로그상 정확한 상태다.
                    if (reply == null || reply.isBlank()) {
                        log.warn("[Call] AI 빈 응답 → 턴 스킵. session={}", sessionId);
                        return;
                    }

                    byte[] wav = clovaVoiceClient.synthesize(reply, AI_SPEAKER);
                    // TTS 송신 성공 시에만 AI 발화를 남긴다.
                    if (sendAudio(session, wav)) {
                        history.add(new AiChatHistoryItem("assistant", reply, LocalDateTime.now()));
                    }
                } catch (Exception e) {
                    // stale/AI/TTS 오류 등: 이번 assistant 턴만 버린다(user 로그는 남는다). 통화는 유지.
                    log.error("[Call] AI 턴 처리 실패 → 턴 폐기. session={}", sessionId, e);
                }
            });
        } catch (RejectedExecutionException e) {
            // 통화 종료와 겹쳐 워커가 이미 내려간 경우. 정상 경로다.
            log.debug("[Call] 워커 종료됨 → 발화를 버림. session={}", sessionId);
        }
    }

    /**
     * 합성된 wav를 이 통화의 소켓에만 바이너리 프레임으로 내려보낸다.
     * CLOVA Voice가 준 wav를 헤더째 그대로 보낸다 — 변환하지 않고 프론트가 헤더로 스펙을 읽는다.
     *
     * @return 실제로 송신했으면 {@code true}. 세션이 닫혔거나 송신에 실패하면 {@code false}
     *         (호출부는 이 값으로 AI 발화를 이력에 남길지 판단한다 — 안 들린 대사는 남기지 않는다).
     */
    private boolean sendAudio(WebSocketSession session, byte[] wav) {
        // 합성 중에 사용자가 끊은 경우. 비동기 워커에선 정상 경로다.
        if (!session.isOpen()) {
            log.debug("[Call] 세션이 닫혀 오디오를 버림. session={}, bytes={}", session.getId(), wav.length);
            return false;
        }
        try {
            // WebSocketSession은 스레드 안전이 아니다. 송신이 겹치면 프레임이 깨지므로 세션별로 직렬화한다.
            synchronized (session) {
                session.sendMessage(new BinaryMessage(wav));
            }
            log.debug("[Call] 오디오 {} bytes 송신. session={}", wav.length, session.getId());
            return true;
        } catch (IOException | IllegalStateException e) {
            // 송신 실패 = 사실상 소켓이 죽음. MVP 정책대로 통화를 끝낸다(STT 스트림까지 정리).
            log.error("[Call] 오디오 송신 실패 → WebSocket 종료. session={}", session.getId(), e);
            terminateCall(session, CloseStatus.SERVER_ERROR);
            return false;
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        completeCall(session.getId());
        log.info("[Call] WebSocket 종료. session={}, status={}", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("[Call] 전송 오류. session={}", session.getId(), exception);
        completeCall(session.getId());
    }

    /** 소켓이 <b>이미 닫힌 뒤</b>의 뒷정리. CLOVA에 half-close를 보낸다. (짝: {@link #terminateCall}) */
    private void completeCall(String sessionId) {
        ActiveCall call = activeCalls.remove(sessionId);
        if (call == null) {
            return;
        }
        call.worker().shutdownNow();
        try {
            call.stream().complete();
        } catch (RuntimeException e) {
            log.warn("[Call] CLOVA 스트림 종료 실패. session={}", sessionId, e);
        }
        finishCall(call.ticket().callId());
    }

    /**
     * 통화를 종결 상태로 전이·저장한다(IN_PROGRESS→COMPLETED / DIALING→CANCELED, 판단은 서비스).
     * 정리 경로(정상 종료·서버 주도 종료) 공통. 상태 저장 실패로 정리를 막지 않도록 로그만 남긴다.
     */
    private void finishCall(Long callId) {
        try {
            callService.finish(callId);
        } catch (RuntimeException e) {
            log.error("[Call] 통화 종료 상태 저장 실패. callId={}", callId, e);
        }
    }

    /**
     * <b>서버가 먼저</b> 통화를 끝낸다 — {@link #completeCall}과 달리 소켓이 살아 있어 우리가 닫고,
     * CLOVA엔 half-close 없이 스트림을 버린다. 원인/로그는 호출부에서 남긴다.
     * 재개설(투명 복원)은 범위 밖 — 닫아서 클라이언트가 재연결하게 둔다.
     */
    private void terminateCall(WebSocketSession session, CloseStatus status) {
        ActiveCall call = activeCalls.remove(session.getId());
        if (call != null) {
            // 워커 스레드 자신이 부를 수도 있다(AI 턴 TTS 송신 실패 경로). shutdownNow는 기다리지 않으므로
            // 자기 자신에게 인터럽트 플래그만 서고 그대로 진행된다 — 교착은 없다.
            call.worker().shutdownNow();
            call.stream().terminate(); // 이후 send()는 무시됨
        }
        // 마감은 ActiveCall이 아니라 세션 티켓 기준 — 스트림 개설이 activeCalls.put 전에 실패해도(call==null)
        // 티켓의 callId로 DIALING을 CANCELED로 닫는다. (put 후 실패 경로도 동일하게 여기서 1회 마감)
        // completeCall과는 맵 원자 remove로 상호배타라 이중 마감이 없다.
        WsTicket ticket = (WsTicket) session.getAttributes()
                .get(WsTicketHandshakeInterceptor.WS_TICKET_ATTRIBUTE);
        if (ticket != null) {
            finishCall(ticket.callId());
        }
        if (session.isOpen()) {
            // 비정상 종료(SERVER_ERROR)는 close 전에 원인을 통지한다 — 정상 완료(NORMAL)엔 통지 없음.
            if (status.getCode() != CloseStatus.NORMAL.getCode()) {
                notifyServerError(session);
            }
            try {
                session.close(status);
            } catch (IOException e) {
                log.warn("[Call] WebSocket 종료 실패. session={}", session.getId(), e);
            }
        }
    }

    /**
     * 서버 주도 종료 직전, 클라이언트가 원인을 감지하도록 JSON 제어 메시지를 보낸다.
     * best-effort — 통지 실패가 정리·종료를 막지 않는다. 원인별 reason 세분화는 후순위.
     */
    private void notifyServerError(WebSocketSession session) {
        try {
            String payload = objectMapper.writeValueAsString(
                    Map.of("type", "error", "reason", "server_error"));
            synchronized (session) { // WebSocketSession은 스레드 안전이 아니다 — 세션별 직렬화
                session.sendMessage(new TextMessage(payload));
            }
        } catch (IOException | RuntimeException e) {
            log.warn("[Call] 종료 통지 전송 실패(무시). session={}", session.getId(), e);
        }
    }

    /**
     * 진행 중인 통화 하나가 들고 있는 것 전부. 수명이 같아 한 홀더로 묶었다 — 정리를 한 번에 하기 위함.
     * 통화 스코프 상태(전사 버퍼·LLM 컨텍스트 등)가 늘면 평행 맵을 만들지 말고 여기 필드로 붙인다.
     *
     * @param worker  통화당 단일 스레드 — 제출 순서 = 실행 순서(AI 응답 순서 보장).
     * @param ticket  핸드셰이크에서 검증된 신원(callId/relationshipId/characterId). AI 배선·전사 저장의 기준.
     * @param history 세션 스코프 대화 이력. <b>워커 스레드만</b> 읽고 쓴다(스레드 confine → 동기화 불필요).
     */
    private record ActiveCall(SttStream stream, ExecutorService worker, WsTicket ticket,
                              List<AiChatHistoryItem> history) {
    }

    /**
     * 통화 하나의 CLOVA STT 업스트림 래퍼. gRPC {@link StreamObserver}는 스레드 안전이 아니므로
     * 전송·종료를 직렬화하고, 종료 후 전송을 막는다. 락은 통화별이라 다른 통화를 막지 않는다.
     */
    private static final class SttStream {

        private final StreamObserver<NestRequest> requestObserver;
        private boolean terminated = false;

        private SttStream(StreamObserver<NestRequest> requestObserver) {
            this.requestObserver = requestObserver;
        }

        /** 종료되지 않았을 때만 요청을 밀어넣는다. */
        synchronized void send(NestRequest request) {
            if (terminated) {
                return;
            }
            requestObserver.onNext(request);
        }

        /** 정상 half-close. 한 번만 수행한다. */
        synchronized void complete() {
            if (terminated) {
                return;
            }
            terminated = true;
            requestObserver.onCompleted();
        }

        /** 비정상 종료 표시. onCompleted는 부르지 않고 이후 전송만 막는다. */
        synchronized void terminate() {
            terminated = true;
        }
    }

    /** CLOVA 인식 결과 콜백. partial/final을 구분해 로그. 에러 시 WebSocket을 닫는다. */
    private final class ClovaResponseObserver implements StreamObserver<NestResponse> {

        private final WebSocketSession session;

        private ClovaResponseObserver(WebSocketSession session) {
            this.session = session;
        }

        @Override
        public void onNext(NestResponse response) {
            String sessionId = session.getId();
            String contents = response.getContents();
            try {
                NestRecognizeResult result = objectMapper.readValue(contents, NestRecognizeResult.class);
                if (!result.isTranscription()) { // config 응답 등
                    log.debug("[Clova] 비전사 응답. session={}, contents={}", sessionId, contents);
                    return;
                }
                String tag = result.isFinal() ? "final" : "partial";
                log.info("[Clova] [{}] session={}, text={}", tag, sessionId, result.text());

                // ⚠ 이 콜백은 즉시 반환해야 한다(스레드가 전 통화 공유). submitChat이 워커로 넘긴다.
                if (result.isFinal()) {
                    submitChat(session, result.text());
                }
            } catch (Exception e) {
                log.warn("[Clova] 응답 파싱 실패. session={}, contents={}", sessionId, contents, e); // 원문 폴백
            }
        }

        @Override
        public void onError(Throwable t) {
            log.error("[Clova] 스트림 오류 → WebSocket 종료. session={}", session.getId(), t);
            terminateCall(session, CloseStatus.SERVER_ERROR);
        }

        @Override
        public void onCompleted() {
            // CLOVA가 스트림을 끝낸 경우(정상/선종료). 남은 맵 정리 + WebSocket 정리.
            log.info("[Clova] 스트림 완료 → WebSocket 정리. session={}", session.getId());
            terminateCall(session, CloseStatus.NORMAL);
        }
    }
}
