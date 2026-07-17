package com.example.umcCall.domain.call.handler;

import com.example.umcCall.domain.call.client.ClovaSpeechClient;
import com.example.umcCall.domain.call.client.ClovaSpeechProperties;
import com.example.umcCall.domain.call.client.ClovaVoiceClient;
import com.example.umcCall.domain.call.dto.NestRecognizeResult;
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
 * 다운스트림은 STT final을 TTS로 합성해 wav를 그대로 내려보낸다. 제어 신호는 텍스트(JSON) 프레임.
 * <p>다운스트림 텍스트 소스는 지금 <b>에코</b>(내 말이 그대로 돌아옴)다 — 이 자리에 LLM이 대체 투입된다.
 */
@Slf4j
@Component
public class CallAudioWebSocketHandler extends AbstractWebSocketHandler {

    // TODO(AI 연동): STT final 결과를 AiConversationService로 연결 필요.
    // partial은 AI로 보내지 말고 클라이언트 자막 전용으로만 사용.

    /** 에코 화자. 캐릭터 개념이 없는 동안의 임시값 — 캐릭터 음성이 들어올 자리다. */
    private static final String ECHO_SPEAKER = "nara";

    private final ClovaSpeechClient clovaSpeechClient;
    private final ClovaVoiceClient clovaVoiceClient;
    private final ObjectMapper objectMapper;

    /** CLOVA 인식 설정(JSON). 한국어 + 침묵(gap) 기반 턴 끝 감지. gapThreshold는 yml에서 온다. */
    private final String configJson;

    /** WS 세션 ID → 진행 중인 통화. WS 수신 · gRPC 콜백 · 워커 세 스레드가 만나는 지점이다. */
    private final ConcurrentHashMap<String, ActiveCall> activeCalls = new ConcurrentHashMap<>();

    public CallAudioWebSocketHandler(ClovaSpeechClient clovaSpeechClient,
                                     ClovaVoiceClient clovaVoiceClient,
                                     ClovaSpeechProperties speechProperties,
                                     ObjectMapper objectMapper) {
        this.clovaSpeechClient = clovaSpeechClient;
        this.clovaVoiceClient = clovaVoiceClient;
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
        try {
            StreamObserver<NestRequest> requestObserver =
                    clovaSpeechClient.openRecognizeStream(new ClovaResponseObserver(session));
            SttStream stream = new SttStream(requestObserver);
            // 워커는 스트림 개설 성공 뒤에 만든다(실패하면 정리할 워커도 없도록).
            // 등록은 CONFIG 전에 — onNext가 처음 뜰 땐 이미 맵에 있어야 한다.
            activeCalls.put(sessionId, new ActiveCall(stream, Executors.newSingleThreadExecutor()));

            // CONFIG 1회 → 이후 오디오는 DATA로. (CLOVA recognize 스트림 규약)
            stream.send(NestRequest.newBuilder()
                    .setType(RequestType.CONFIG)
                    .setConfig(NestConfig.newBuilder().setConfig(configJson).build())
                    .build());

            log.info("[Call] WebSocket 연결 · CLOVA 스트림 개설. session={}", sessionId);
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
     * final 전사를 TTS로 합성해 다운스트림으로 돌려보낸다.
     * 호출자(gRPC 콜백)를 잡아두지 않도록 통화 워커에 넘기고 즉시 반환한다.
     * <p>★ 나중에 LLM 호출·전사 DB 저장이 들어올 자리가 이 워커 안이다.
     */
    private void submitEcho(WebSocketSession session, String text) {
        String sessionId = session.getId();
        if (text == null || text.isBlank()) {
            log.debug("[Call] 빈 final → 에코 생략. session={}", sessionId);
            return;
        }
        ActiveCall call = activeCalls.get(sessionId);
        if (call == null) {
            log.debug("[Call] 통화가 없어 에코를 버림. session={}", sessionId);
            return;
        }
        try {
            call.worker().execute(() -> {
                try {
                    byte[] wav = clovaVoiceClient.synthesize(text, ECHO_SPEAKER);
                    sendAudio(session, wav);
                } catch (Exception e) {
                    // 파이프라인 에러는 서버 로깅 전용. 문장 하나 실패로 통화를 끊지 않는다.
                    log.error("[Call] 에코 합성/송신 실패. session={}", sessionId, e);
                }
            });
        } catch (RejectedExecutionException e) {
            // 통화 종료와 겹쳐 워커가 이미 내려간 경우. 정상 경로다.
            log.debug("[Call] 워커 종료됨 → 에코를 버림. session={}", sessionId);
        }
    }

    /**
     * 합성된 wav를 이 통화의 소켓에만 바이너리 프레임으로 내려보낸다.
     * CLOVA Voice가 준 wav를 헤더째 그대로 보낸다 — 변환하지 않고 프론트가 헤더로 스펙을 읽는다.
     */
    private void sendAudio(WebSocketSession session, byte[] wav) {
        // 합성 중에 사용자가 끊은 경우. 비동기 워커에선 정상 경로다.
        if (!session.isOpen()) {
            log.debug("[Call] 세션이 닫혀 오디오를 버림. session={}, bytes={}", session.getId(), wav.length);
            return;
        }
        try {
            // WebSocketSession은 스레드 안전이 아니다. 송신이 겹치면 프레임이 깨지므로 세션별로 직렬화한다.
            synchronized (session) {
                session.sendMessage(new BinaryMessage(wav));
            }
            log.debug("[Call] 오디오 {} bytes 송신. session={}", wav.length, session.getId());
        } catch (IOException | IllegalStateException e) {
            // 송신 실패 = 사실상 소켓이 죽음. MVP 정책대로 통화를 끝낸다(STT 스트림까지 정리).
            log.error("[Call] 오디오 송신 실패 → WebSocket 종료. session={}", session.getId(), e);
            terminateCall(session, CloseStatus.SERVER_ERROR);
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
    }

    /**
     * <b>서버가 먼저</b> 통화를 끝낸다 — {@link #completeCall}과 달리 소켓이 살아 있어 우리가 닫고,
     * CLOVA엔 half-close 없이 스트림을 버린다. 원인/로그는 호출부에서 남긴다.
     * 재개설(투명 복원)은 범위 밖 — 닫아서 클라이언트가 재연결하게 둔다.
     */
    private void terminateCall(WebSocketSession session, CloseStatus status) {
        ActiveCall call = activeCalls.remove(session.getId());
        if (call != null) {
            // 워커 스레드 자신이 부를 수도 있다(에코 송신 실패 경로). shutdownNow는 기다리지 않으므로
            // 자기 자신에게 인터럽트 플래그만 서고 그대로 진행된다 — 교착은 없다.
            call.worker().shutdownNow();
            call.stream().terminate(); // 이후 send()는 무시됨
        }
        if (session.isOpen()) {
            try {
                session.close(status);
            } catch (IOException e) {
                log.warn("[Call] WebSocket 종료 실패. session={}", session.getId(), e);
            }
        }
    }

    /**
     * 진행 중인 통화 하나가 들고 있는 것 전부. 수명이 같아 한 홀더로 묶었다 — 정리를 한 번에 하기 위함.
     * 통화 스코프 상태(전사 버퍼·LLM 컨텍스트 등)가 늘면 평행 맵을 만들지 말고 여기 필드로 붙인다.
     *
     * @param worker 통화당 단일 스레드 — 제출 순서 = 실행 순서(에코 순서 보장).
     */
    private record ActiveCall(SttStream stream, ExecutorService worker) {
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

                // ⚠ 이 콜백은 즉시 반환해야 한다(스레드가 전 통화 공유). submitEcho가 워커로 넘긴다.
                if (result.isFinal()) {
                    submitEcho(session, result.text());
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
