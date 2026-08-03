package com.example.umcCall.domain.call.handler;

import com.example.umcCall.domain.call.client.ClovaSpeechClient;
import com.example.umcCall.domain.call.client.ClovaSpeechProperties;
import com.example.umcCall.domain.ai.dto.AiChatHistoryItem;
import com.example.umcCall.domain.call.client.ClovaVoiceClient;
import com.example.umcCall.domain.call.dto.NestRecognizeResult;
import com.example.umcCall.domain.call.enums.CallSpeaker;
import com.example.umcCall.domain.call.recording.CallRecorder;
import com.example.umcCall.domain.call.recording.CallRecordingService;
import com.example.umcCall.domain.call.service.CallArtifactRegistry;
import com.example.umcCall.domain.call.service.CallConversationService;
import com.example.umcCall.domain.call.service.CallHistoryService;
import com.example.umcCall.domain.call.service.CallService;
import com.example.umcCall.domain.call.service.CallSummaryService;
import com.example.umcCall.domain.call.service.CallVoiceResolver;
import com.example.umcCall.domain.call.service.SentenceBuffer;
import com.example.umcCall.domain.call.event.CallEndedEvent;
import com.example.umcCall.domain.call.ticket.WsTicket;
import com.example.umcCall.domain.call.ticket.WsTicketHandshakeInterceptor;
import com.example.umcCall.domain.image.enums.TTSVoice;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import com.nbp.cdncp.nest.grpc.proto.v1.NestConfig;
import com.nbp.cdncp.nest.grpc.proto.v1.NestData;
import com.nbp.cdncp.nest.grpc.proto.v1.NestRequest;
import com.nbp.cdncp.nest.grpc.proto.v1.NestResponse;
import com.nbp.cdncp.nest.grpc.proto.v1.RequestType;
import io.grpc.stub.StreamObserver;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

/**
 * 통화 오디오 양방향 채널 핸들러. 통화마다 분리해 처리한다.
 * 업스트림은 바이너리 프레임(raw PCM 16kHz/모노/16-bit)을 CLOVA STT로 중계하고,
 * 다운스트림은 STT final을 AI(chat)로 넘겨 응답 대사를 TTS로 합성해 wav를 그대로 내려보낸다.
 * <p>제어 신호는 텍스트(JSON) 프레임이고 봉투는 {@code {"type":..., "data":{...}}} 하나로 통일한다
 * ({@link MessageType} 참고 — 이름을 바꾸면 클라이언트가 깨진다).
 * <p>AI로 넘어가는 건 <b>final뿐</b>이다. partial은 <b>클라이언트로 보내지 않고</b>, 서버에서 로그와
 * <b>끼어들기 트리거</b>({@link #cancelSpeakingTurn})로만 쓴다 — 실시간 자막을 붙일 때 다운스트림을 배선한다.
 */
@Slf4j
@Component
public class CallAudioWebSocketHandler extends AbstractWebSocketHandler {

    private final ClovaSpeechClient clovaSpeechClient;
    private final ClovaVoiceClient clovaVoiceClient;
    private final CallConversationService callConversationService;
    private final CallService callService;
    private final CallHistoryService callHistoryService;
    private final CallRecordingService callRecordingService;
    private final CallSummaryService callSummaryService;
    private final CallVoiceResolver callVoiceResolver;
    private final CallArtifactRegistry callArtifactRegistry;
    private final ObjectMapper objectMapper;

    /** CLOVA 인식 설정(JSON). 한국어 + 침묵(gap) 기반 턴 끝 감지. gapThreshold는 yml에서 온다. */
    private final String configJson;

    /** WS 세션 ID → 진행 중인 통화. WS 수신 · gRPC 콜백 · 워커 세 스레드가 만나는 지점이다. */
    private final ConcurrentHashMap<String, ActiveCall> activeCalls = new ConcurrentHashMap<>();

    /**
     * 끼어들기 통지 전용 스레드(전 통화 공유). 트리거는 gRPC 콜백인데 <b>그 스레드에서 소켓을 쓰면 안 되므로</b>
     * 여기로 넘긴다 — {@link #sendControl}은 세션 락을 잡아 워커의 wav 송신과 겹치면 잠깐 대기하고,
     * 그 대기가 콜백 스레드에 걸리면 <b>다른 통화의 STT까지</b> 밀린다.
     * <p>단일 스레드로 충분하다: 프레임이 작고(수십 바이트) 발화당 한 번뿐이다.
     */
    private final ExecutorService controlNotifier = Executors.newSingleThreadExecutor();

    public CallAudioWebSocketHandler(ClovaSpeechClient clovaSpeechClient,
                                     ClovaVoiceClient clovaVoiceClient,
                                     CallConversationService callConversationService,
                                     CallService callService,
                                     CallHistoryService callHistoryService,
                                     ClovaSpeechProperties speechProperties,
                                     CallRecordingService callRecordingService,
                                     CallSummaryService callSummaryService,
                                     CallVoiceResolver callVoiceResolver,
                                     CallArtifactRegistry callArtifactRegistry,
                                     ObjectMapper objectMapper) {
        this.callSummaryService = callSummaryService;
        this.callArtifactRegistry = callArtifactRegistry;
        this.clovaSpeechClient = clovaSpeechClient;
        this.clovaVoiceClient = clovaVoiceClient;
        this.callConversationService = callConversationService;
        this.callService = callService;
        this.callHistoryService = callHistoryService;
        this.callRecordingService = callRecordingService;
        this.callVoiceResolver = callVoiceResolver;
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
            // 등록(put)은 최대한 빨리 — 등록 전에 도착한 오디오 프레임은 버려진다.
            // ⚠ 맥락은 빈 상태로 시작한다(가변 리스트 — 통화 턴이 여기에 append된다). 채팅에서 이어받는 시딩을
            // 걷어낸 이유는 AI 서버가 채팅·통화를 통합해 자체 저장하고 그 기록을 우리가 보낸 history보다
            // 우선하기 때문이다 — 시딩해봐야 무시되고, 이어받기 깊이는 이제 AI 서버가 정한다.
            List<AiChatHistoryItem> history = new ArrayList<>();
            ExecutorService worker = Executors.newSingleThreadExecutor();
            // ⚠ 화자 해석은 여기서 <b>딱 한 번</b>이다. TTS는 턴마다 문장 수만큼 불리므로 그쪽에서 해석하면
            // 문장 하나에 DB가 두 번 나간다. 실패해도 기본 목소리가 돌아올 뿐 통화는 그대로 이어진다.
            TTSVoice voice = callVoiceResolver.resolve(ticket.characterId());
            activeCalls.put(sessionId, new ActiveCall(session, stream, worker, ticket, history,
                    new TurnGate(), new CallRecorder(), voice));

            // CONFIG 1회 → 이후 오디오는 DATA로. (CLOVA recognize 스트림 규약)
            stream.send(NestRequest.newBuilder()
                    .setType(RequestType.CONFIG)
                    .setConfig(NestConfig.newBuilder().setConfig(configJson).build())
                    .build());

            // DIALING/PENDING → IN_PROGRESS.
            // ⚠ 실패하면 소켓을 닫는다 — 스위퍼가 먼저 MISSED/CANCELED로 마감한 통화일 수 있고,
            // 그 경우 오디오는 흐르는데 startedAt이 없어 종료 시 complete()가 터진다(유령 통화).
            try {
                callService.connect(ticket.callId());
            } catch (RuntimeException e) {
                log.error("[Call] 통화 연결 상태 전이 실패 → WebSocket 종료. session={}, callId={}",
                        sessionId, ticket.callId(), e);
                terminateCall(session, CloseStatus.SERVER_ERROR);
                return;
            }

            // connect() 성공 뒤에만 보낸다 — 이 신호는 "서버 준비됨"이자 "이 통화는 IN_PROGRESS"다.
            sendControl(session, MessageType.CALL_READY, Map.of("callId", ticket.callId()));

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
            return;
        }
        // ⚠ STT 중계 뒤에 녹음한다 — 이 메서드는 WS 수신 스레드라, 녹음을 앞에 두면 그만큼
        // STT 도착이 밀려 턴 감지·끼어들기가 늦어진다. 녹음은 밀려도 되는 쪽이다.
        call.recorder().writeUpstream(chunk);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 제어 신호(통화 시작/끝). 파싱/상태머신은 후순위 — 지금은 로그만.
        log.info("[Call] 제어 메시지 수신. session={}, payload={}", session.getId(), message.getPayload());
    }

    /**
     * final 발화를 AI로 넘겨 응답 대사를 TTS로 합성해 다운스트림으로 돌려보낸다.
     * 호출자(gRPC 콜백)를 잡아두지 않도록 통화 워커에 넘기고 즉시 반환한다.
     * <p>AI 호출은 <b>스트리밍(SSE)</b>이라 워커를 수 초간 잡는다 — 워커 단일 스레드가 턴 순서를 보장한다.
     * 대사는 문장이 완성되는 대로 합성·송신하므로, 첫 문장이 나가는 시점이 곧 사용자가 듣는 시점이다.
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
        TurnGate turnGate = call.turnGate();
        // TTFA 원점. 워커 대기까지 포함해야 사용자가 겪는 침묵과 같아지므로 제출 전에 찍는다.
        long turnStartedAt = System.nanoTime();
        try {
            call.worker().execute(() -> {
                // 끼어들기 판정 기준. 워커(단일 스레드)만 턴을 열므로 여기서 번호를 잡는다.
                long turn = turnGate.begin();
                try {
                    // 이벤트 로그 방식: 발화가 '실제로 일어난 순간'에 독립적으로 append한다(user/ai 짝 아님).
                    // 워커 단일 스레드가 유일한 writer라 로그 접근은 스레드 confine(동기화 불필요).

                    // STT final 확정 → 사용자 발화를 먼저 남긴다. AI 응답 성공 여부와 무관한 사실이다.
                    // (respond가 실패해도 남는다 — 연속 user는 이 모델에서 정상.) role은 계약대로 소문자.
                    history.add(new AiChatHistoryItem("user", text, LocalDateTime.now()));
                    persistHistory(sessionId, ticket.callId(), CallSpeaker.USER, text);

                    // AI 대사를 조각으로 받아(SSE) 문장이 완성될 때마다 합성·송신한다.
                    // 대사 전체를 기다리지 않는 게 핵심 — LLM이 나머지를 만드는 시간이 체감 지연에서 빠진다.
                    SentenceBuffer sentences = new SentenceBuffer();
                    SpeakingTurn speaking = new SpeakingTurn(session, turnGate, turn, turnStartedAt,
                            call.recorder(), call.voice().speakerId());
                    try {
                        callConversationService.respondStream(
                                ticket.characterId(), ticket.relationshipId(), history,
                                chunk -> {
                                    for (String sentence : sentences.feed(chunk)) {
                                        speaking.speak(sentence);
                                    }
                                });
                        // 마지막 문장은 대개 종결 부호 뒤에 아무것도 안 와서 버퍼에 남는다.
                        sentences.flush().ifPresent(speaking::speak);
                    } catch (TurnStoppedException e) {
                        // 끼어들기 또는 소켓 종료. 여기까지 말한 건 사용자가 들었으므로 아래에서 그대로 남긴다.
                        log.info("[Call] 남은 대사 중단. session={}, turn={}, 사유={}",
                                sessionId, turn, e.getMessage());
                    } catch (Exception e) {
                        // stale/AI/TTS 오류: 말한 부분까지만 남기고 이 턴을 접는다. 통화는 유지.
                        log.error("[Call] AI 턴 처리 실패 → 말한 부분까지만 남긴다. session={}", sessionId, e);
                    }

                    // ⚠ 실제로 <b>내보낸</b> 대사만 남긴다 — 안 들린 대사를 남기면 다음 턴이 사용자 모르는
                    // 맥락 위에서 나온다. 아무것도 못 내보냈으면 assistant는 안 남는다
                    // (= "사용자는 말했고 AI는 아무 말 안 함"으로 이벤트 로그상 정확한 상태다).
                    String spoken = speaking.spokenText();
                    if (!spoken.isEmpty()) {
                        history.add(new AiChatHistoryItem("assistant", spoken, LocalDateTime.now()));
                        persistHistory(sessionId, ticket.callId(), CallSpeaker.AI, spoken);
                    }
                } catch (Exception e) {
                    // 이력·전사 저장 등 위 블록 밖의 실패. 이번 턴만 버리고 통화는 유지한다.
                    log.error("[Call] AI 턴 처리 실패 → 턴 폐기. session={}", sessionId, e);
                }
            });
        } catch (RejectedExecutionException e) {
            // 통화 종료와 겹쳐 워커가 이미 내려간 경우. 정상 경로다.
            log.debug("[Call] 워커 종료됨 → 발화를 버림. session={}", sessionId);
        }
    }

    /**
     * 이 인식 결과에 <b>실제 말</b>이 담겼는가. 끼어들기 판정의 전제다.
     * <p>⚠ 빈 결과가 취소로 이어지면 <b>AI가 영영 말을 못 한다</b> — 그런데 로그엔 "끼어들기"만 찍혀
     * 원인 추적이 매우 어렵다. 지금은 CLOVA CONFIG의 {@code skipEmptyText}가 빈 결과를 막아주지만,
     * <b>설정 한 줄에 기대지 않으려고</b> 여기서 한 번 더 본다(설정을 바꿔도 이 불변식은 유지된다).
     * <p>⚠ 막아주는 건 "빈 결과"까지다 — <b>소음·에코가 단어로 인식되면</b> 여기를 통과해 AI를 끊는다.
     * 그건 FE의 에코 제거(AEC)와 마이크 환경이 담당한다.
     */
    private static boolean hasSpeech(NestRecognizeResult result) {
        return result.text() != null && !result.text().isBlank();
    }

    /**
     * 사용자가 말을 시작했다(STT partial) → 진행 중인 AI 턴을 취소로 표시한다 = <b>끼어들기</b>.
     * <p>⚠ 이 메서드는 전 통화가 공유하는 gRPC 콜백 스레드에서 불린다 — 원자 변수 세팅만 하고 즉시 반환한다.
     * <p>막는 방법이 구간마다 다르다: <b>아직 전송 전</b>인 대사는 워커가 합성·송신을 건너뛰면 되지만
     * (LLM 대기 + TTS 합성 = 턴 지연의 대부분), <b>이미 내려보낸</b> wav는 회수할 수 없어
     * 클라이언트가 재생 큐를 비워야 한다. 세션이 없으면 정리 중이라 no-op.
     */
    private void cancelSpeakingTurn(String sessionId) {
        ActiveCall call = activeCalls.get(sessionId);
        if (call != null && call.turnGate().cancelCurrent()) {
            notifySpeechCanceled(call);
        }
    }

    /**
     * 이미 내려보낸 AI 음성의 재생을 멈추라고 클라이언트에 알린다({@link #controlNotifier}가 실제로 보낸다).
     * <p>보내는 시점엔 <b>다음 턴 오디오가 아직 없어</b>(사용자가 말하는 중 → final → LLM) 클라이언트가
     * 큐를 통째로 비워도 안전하다 — WebSocket이 프레임 순서를 지킨다.
     * <p>best-effort다: 통지가 늦거나 유실돼도 통화는 그대로 진행된다(문장 하나가 더 들릴 뿐).
     */
    private void notifySpeechCanceled(ActiveCall call) {
        call.recorder().resetAiCursor(); // 녹음 타임라인도 같이 맞춘다 — 이유는 resetAiCursor 참고
        try {
            controlNotifier.execute(() -> sendControl(call.session(),
                    MessageType.AI_SPEECH_CANCELED,
                    Map.of("callId", call.ticket().callId())));
        } catch (RejectedExecutionException e) {
            // 앱 종료로 통지 스레드가 내려간 뒤. 곧 소켓도 닫히므로 통지할 이유가 없다.
            log.debug("[Call] 통지 스레드 종료됨 → 끼어들기 통지를 버림. session={}", call.session().getId());
        }
    }

    /**
     * AI 턴 하나가 <b>말하는 동안</b>의 상태. 문장이 완성될 때마다 합성·송신하고, <b>실제로 내보낸</b> 대사를 모은다.
     * <p>통화 워커 스레드에서만 쓴다(SSE 조각 콜백도 같은 스레드라 confine이 유지된다).
     * <p>끼어들기 확인이 <b>문장마다 두 번</b>(합성 전·송신 전) 들어간다 — 대사를 한 번에 합성하던 때보다
     * 촘촘해져서, 이미 내보내 못 막는 구간이 문장 하나 길이로 줄어든다.
     */
    private final class SpeakingTurn {

        private final WebSocketSession session;
        private final TurnGate turnGate;
        private final long turn;
        private final long turnStartedAt;
        private final CallRecorder recorder;
        /** 이 통화 AI의 목소리(CLOVA 화자 ID). 연결 시 해석해 둔 값을 그대로 받는다. */
        private final String speaker;
        private final StringBuilder spoken = new StringBuilder();
        private boolean firstAudioSent;

        private SpeakingTurn(WebSocketSession session, TurnGate turnGate, long turn,
                             long turnStartedAt, CallRecorder recorder, String speaker) {
            this.speaker = speaker;
            this.session = session;
            this.turnGate = turnGate;
            this.turn = turn;
            this.turnStartedAt = turnStartedAt;
            this.recorder = recorder;
        }

        /**
         * 문장 하나를 합성해 이 통화의 소켓으로 내보낸다.
         * 더 말할 수 없는 상태면 {@link TurnStoppedException}으로 남은 문장까지 통째로 접는다 —
         * 계속 스트림을 읽어봐야 워커가 묶여 <b>사용자가 방금 끼어들며 만든 다음 턴</b>이 밀린다.
         */
        private void speak(String sentence) {
            // ⚠ 발음할 게 없는 조각(이모지·구두점만)은 CLOVA가 400을 준다("TN result is empty").
            // AI 대사가 이모지로 끝나면 flush 꼬리가 딱 이 모양이라 실전에서 매번 걸린다.
            // 턴을 접지 않고 이 조각만 건너뛴다 — 어차피 소리로 나갈 내용이 아니다.
            if (!isSpeakable(sentence)) {
                log.debug("[Call] 합성할 내용이 없어 건너뜀. session={}, text={}", session.getId(), sentence);
                return;
            }
            // 끼어들기 확인 ①: 합성 전. 어차피 못 내보낼 문장이라 TTS 호출·비용이 통째로 낭비다.
            if (turnGate.isCanceled(turn)) {
                throw new TurnStoppedException("끼어들기(합성 전)");
            }
            long llmMs = firstAudioSent ? -1 : elapsedMs(turnStartedAt);

            long ttsStartedAt = System.nanoTime();
            byte[] wav = clovaVoiceClient.synthesize(sentence, speaker);
            long ttsMs = elapsedMs(ttsStartedAt);

            // 끼어들기 확인 ②: 합성 중에 말을 시작한 경우. 아직 전송 전이라 사용자에겐 애초에 들리지 않는다.
            if (turnGate.isCanceled(turn)) {
                throw new TurnStoppedException("끼어들기(송신 전)");
            }
            if (!sendAudio(session, wav)) {
                throw new TurnStoppedException("소켓 종료");
            }
            turnGate.markSpoken(turn); // 여기부터 회수 불가 구간 — 끼어들면 통지가 나간다
            // 실제로 <b>내보낸</b> 소리만 녹음에 남는다 — 이력·전사와 같은 기준이라 셋이 어긋나지 않는다.
            recorder.writeAiWav(wav);
            if (!firstAudioSent) {
                firstAudioSent = true;
                logTurnLatency(session.getId(), turnStartedAt, llmMs, ttsMs);
            }
            if (!spoken.isEmpty()) {
                spoken.append(' ');
            }
            spoken.append(sentence);
        }

        /** 이 턴에서 실제로 내보낸 대사 전체. 아무것도 못 내보냈으면 빈 문자열. */
        private String spokenText() {
            return spoken.toString();
        }
    }

    /**
     * 합성해서 들려줄 내용이 있는가. 글자·숫자가 하나도 없으면(이모지·구두점만) CLOVA가 정규화 결과가 비었다며
     * 400을 돌려준다 — 보내기 전에 걸러야 그 턴이 오류로 끊기지 않는다.
     */
    private static boolean isSpeakable(String text) {
        return text.codePoints().anyMatch(Character::isLetterOrDigit);
    }

    /**
     * 더 말할 수 없어 이 턴을 접는다(끼어들기 · 소켓 종료). <b>실패가 아니라 제어 흐름</b>이라
     * 스택트레이스를 만들지 않고, 여기까지 말한 대사는 정상적으로 이력·전사에 남는다.
     */
    private static final class TurnStoppedException extends RuntimeException {
        private TurnStoppedException(String reason) {
            super(reason, null, false, false);
        }
    }

    /** 경과 시간(ms). 벽시계가 아니라 단조 시계라 시각 보정에 흔들리지 않는다. */
    private static long elapsedMs(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000L;
    }

    /**
     * 턴 지연을 한 줄로 남긴다. <b>TTFA</b>(Time-To-First-Audio) = STT final 도착 → 첫 오디오 송신.
     * 지연 개선의 전후 비교는 이 로그가 기준이다 — 지우거나 레벨을 낮추면 측정 수단이 사라진다.
     * <p>{@code llmMs}는 <b>첫 문장이 준비되기까지</b>(final 도착 → 합성 시작), {@code ttsMs}는 그 문장의
     * 합성 시간이다. 스트리밍 전에는 llmMs가 "대사 전체 생성"이었다 — 값을 비교할 땐 의미 차이를 감안할 것.
     * <p>⚠ 사용자 체감 침묵은 여기에 <b>{@code gapThreshold}(기본 700ms)가 더 얹힌다</b> — 말이 끝난 순간이
     * 아니라 침묵이 그만큼 이어진 뒤에야 final이 오기 때문이다. 서버는 말이 끝난 시각을 관측할 수 없다.
     */
    private void logTurnLatency(String sessionId, long turnStartedAt, long llmMs, long ttsMs) {
        log.info("[Call] 턴 지연. session={}, ttfaMs={}, llmMs={}, ttsMs={}",
                sessionId, elapsedMs(turnStartedAt), llmMs, ttsMs);
    }

    /**
     * 전사 한 줄을 DB에 남긴다. 워커 스레드에서 발화가 실제로 일어난 순간(USER final · AI TTS 송신 성공)마다 호출된다.
     * <p>저장 실패로 통화·턴을 끊지 않는다 — 로그만 남긴다(connect/finish 상태저장 실패 정책과 동일).
     * {@code chat()}의 느린 REST는 이 저장 트랜잭션 밖에서 이미 끝났다.
     */
    private void persistHistory(String sessionId, Long callId, CallSpeaker speaker, String content) {
        try {
            callHistoryService.appendHistory(callId, speaker, content);
        } catch (RuntimeException e) {
            log.error("[Call] 전사 저장 실패(통화는 유지). session={}, callId={}, speaker={}",
                    sessionId, callId, speaker, e);
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

    /**
     * 서비스가 마감한 통화(사용자 REST 종료 / 시간 상한 스위퍼)의 남은 소켓·STT 스트림·워커를 닫는다.
     * 세션이 없으면 no-op이다 — 스위퍼 마감은 소켓이 이미 죽어 있는 경우가 대부분이다.
     * <p>맵이 sessionId 키라 callId는 순회로 찾는다 — 역색인 맵은 동기화 문제가 생기고, 종료는 드문 이벤트다.
     * <p>정상 종료라 {@link CloseStatus#NORMAL}로 닫고, 에러 대신 {@code CALL_ENDED}로 사유를 통지한다.
     * 상태가 이미 종결이라 {@code terminateCall} 안의 마감 호출은 no-op으로 지나간다.
     * <p>⚠ <b>산출물(녹음·요약)을 기다리지 않는다.</b> 이 리스너는 {@code PATCH /calls/{id}/end}의
     * 요청 스레드에서 응답 직전에 돌기 때문에, 여기서 기다리면 <b>끊기 버튼이 그만큼 멈춘다</b>.
     * 기다리는 건 프론트의 종료 화면 조회({@code GET /calls/{callId}?wait=true})가 맡는다 —
     * 그쪽은 이 경로뿐 아니라 <b>소켓이 죽어 끝난 통화까지</b> 덮는다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCallEnded(CallEndedEvent event) {
        Long callId = event.callId();
        activeCalls.values().stream()
                .filter(call -> call.ticket().callId().equals(callId))
                .findFirst()
                .ifPresent(call -> {
                    log.info("[Call] 통화 마감({})으로 세션 정리. callId={}, session={}",
                            event.reason(), callId, call.session().getId());
                    terminateCall(call.session(), CloseStatus.NORMAL, event);
                });
    }

    /**
     * 앱 종료 시 남은 통화를 마감한다(IN_PROGRESS → COMPLETED + 소켓·스트림 정리).
     *
     * <p>종료 순서가 <b>{@code @PreDestroy} → 빈 파괴 → 웹서버 정지</b>라, 컨테이너가 소켓을 닫아 부르는
     * {@code afterConnectionClosed}는 DataSource가 이미 닫힌 뒤에 도착한다 → {@code finish()}가 실패하고
     * 통화가 {@code IN_PROGRESS}로 남는다. 이 시점엔 세션과 DB가 모두 살아 있어 정상 마감이 가능하다.
     * (2026-07-26 실측)
     *
     * <p>NORMAL로 닫는다: 통화는 실제로 성립했으므로 {@code COMPLETED}가 맞고 {@code callTime}도 남는다.
     * {@code CALL_ENDED}는 보내지 않는다 — 마감을 여기서 하므로 {@code callTime}을 알 수 없고, 곧 서버가
     * 죽어 클라이언트가 그 통지로 할 수 있는 일도 없다.
     *
     * <p>크래시·SIGKILL·half-open은 이 경로로 못 덮는다 — {@code call.timeout.max-call-minutes} 상한
     * 스위퍼({@code CallService#closeOverrunCall})가 그 몫을 맡는다.
     */
    @PreDestroy
    void closeActiveCallsOnShutdown() {
        // 통지 스레드를 먼저 내린다 — 곧 소켓을 닫으므로 대기 중인 통지는 보낼 곳이 없다.
        controlNotifier.shutdownNow();

        List<ActiveCall> remaining = new ArrayList<>(activeCalls.values());
        if (remaining.isEmpty()) {
            return;
        }
        log.info("[Call] 앱 종료 — 진행 중인 통화 {}건 마감.", remaining.size());
        remaining.forEach(call -> terminateCall(call.session(), CloseStatus.NORMAL));
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
        // 클라이언트가 먼저 끊은 경로다 — 기다려 줄 요청 스레드가 없으니 시작만 하고 버린다.
        // (이 통화는 프론트가 나중에 조회할 때 PROCESSING일 수 있다)
        finishArtifacts(call);
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

    /** 통지 없이 끝낸다 — 비정상 종료(원인은 {@code ERROR}로 나간다)와 앱 종료가 쓴다. */
    private void terminateCall(WebSocketSession session, CloseStatus status) {
        terminateCall(session, status, null);
    }

    /**
     * 통화 마감 후 만들어지는 산출물(녹음·요약) 생성을 <b>시작</b>하고, 기다릴 수 있도록 등록해 둔다.
     *
     * <p>⚠ <b>여기가 모든 종료 경로가 지나는 하나뿐인 지점</b>이다(끊기 버튼 · 시간 상한 스위퍼 · 소켓 끊김 ·
     * 서버 오류 · 앱 종료). 그래서 {@link CallArtifactRegistry} 등록도 여기서 한다 — 이 한 줄 덕에
     * 프론트는 <b>통화가 어떻게 끝났든</b> 상세 조회({@code ?wait=true})로 산출물을 기다릴 수 있다.
     *
     * <p>⚠ <b>여기서 기다리지 않는다.</b> 이 메서드는 WS 수신 스레드·gRPC 오류 경로·앱 종료·사용자 REST
     * 종료가 모두 지난다 — 기다리면 각각 <b>오디오 수신이 멈추거나, 끊기 버튼이 멈춘다</b>. 기다리는 주체는
     * 통화가 아니라 <b>프론트의 조회 요청</b>이다.
     *
     * <p>⚠ <b>녹음·요약은 병렬이어야 한다</b> — 직렬이면 준비 시간이 업로드 + LLM의 <b>합</b>이 된다.
     * 서로 다른 풀에서 돌기 때문에 {@code allOf}로 묶기만 하면 병렬이다.
     * 둘 다 fail-open이라 이 future는 사실상 실패하지 않는다(내부에서 삼키고 상태만 남긴다).
     */
    private void finishArtifacts(ActiveCall call) {
        Long callId = call.ticket().callId();
        CompletableFuture<Void> recording = call.recorder().finish()
                .map(wav -> callRecordingService.save(callId, wav))
                .orElseGet(() -> CompletableFuture.completedFuture(null));
        CompletableFuture<Void> summary = callSummaryService.generate(callId);
        callArtifactRegistry.register(callId, CompletableFuture.allOf(recording, summary));
    }

    /**
     * <b>서버가 먼저</b> 통화를 끝낸다 — {@link #completeCall}과 달리 소켓이 살아 있어 우리가 닫고,
     * CLOVA엔 half-close 없이 스트림을 버린다. 원인/로그는 호출부에서 남긴다.
     * 재개설(투명 복원)은 범위 밖 — 닫아서 클라이언트가 재연결하게 둔다.
     *
     * @param endNotice 정상 종료 통지({@code CALL_ENDED}) 내용. {@code null}이면 통지하지 않는다 —
     *                  앱 종료 경로가 그렇다(마감을 여기서 하므로 {@code callTime}을 알 방법이 없고,
     *                  그 직후 서버가 죽어 클라이언트가 할 수 있는 일도 없다).
     */
    private void terminateCall(WebSocketSession session, CloseStatus status,
                               CallEndedEvent endNotice) {
        ActiveCall call = activeCalls.remove(session.getId());
        if (call != null) {
            // 워커 스레드 자신이 부를 수도 있다(AI 턴 TTS 송신 실패 경로). shutdownNow는 기다리지 않으므로
            // 자기 자신에게 인터럽트 플래그만 서고 그대로 진행된다 — 교착은 없다.
            call.worker().shutdownNow();
            call.stream().terminate(); // 이후 send()는 무시됨
            // ⚠ 시작만 하고 기다리지 않는다 — 이 메서드는 WS 수신 스레드·사용자 REST 종료도 부른다.
            // 기다리는 건 프론트의 조회 요청(GET /calls/{callId}?wait=true)이다.
            finishArtifacts(call);
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
            // close 전에 이유를 통지한다 — 정상 종료는 CALL_ENDED, 비정상(SERVER_ERROR)은 ERROR.
            // 소켓 close 프레임만으론 프론트가 "서버가 끝냈다"와 "네트워크가 끊겼다"를 구별할 수 없다.
            if (endNotice != null) {
                notifyCallEnded(session, endNotice);
            } else if (status.getCode() != CloseStatus.NORMAL.getCode()) {
                notifyServerError(session);
            }
            try {
                session.close(status);
            } catch (IOException e) {
                log.warn("[Call] WebSocket 종료 실패. session={}", session.getId(), e);
            }
        }
    }

    /** 서버 주도 종료 직전 통지. 뒤이어 소켓이 닫힌다. */
    private void notifyServerError(WebSocketSession session) {
        sendControl(session, MessageType.ERROR, Map.of("reason", "SERVER_ERROR"));
    }

    /**
     * 정상 종료 직전 통지. 프론트는 {@code reason}으로 종료 화면 문구를 고르고 {@code callTime}으로 통화
     * 시간을 표시한다 — 서버 계산값이라 프론트 자체 측정과 어긋나지 않는다.
     */
    private void notifyCallEnded(WebSocketSession session, CallEndedEvent notice) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("callId", notice.callId());
        data.put("reason", notice.reason().name());
        // callTime은 연결됐던 통화에만 있다. null이면 키를 뺀다 — 0초 통화로 오해되지 않게.
        if (notice.callTime() != null) {
            data.put("callTime", notice.callTime());
        }
        sendControl(session, MessageType.CALL_ENDED, data);
    }

    /**
     * 서버 → 클라이언트 제어 메시지를 보낸다. 봉투는 {@code {"type":..., "data":{...}}}로 통일한다 —
     * 프론트가 {@code type}으로 분기하고 {@code data}만 파싱하면 되도록.
     * <p>best-effort다: 제어 메시지 실패로 통화를 끊지 않는다(오디오 송신 실패와 다르다).
     */
    private void sendControl(WebSocketSession session, MessageType type, Map<String, Object> data) {
        if (!session.isOpen()) {
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(
                    Map.of("type", type.name(), "data", data));
            synchronized (session) { // WebSocketSession은 스레드 안전이 아니다 — 세션별 직렬화
                session.sendMessage(new TextMessage(payload));
            }
        } catch (IOException | RuntimeException e) {
            log.warn("[Call] 제어 메시지 전송 실패(무시). session={}, type={}", session.getId(), type, e);
        }
    }

    /**
     * 서버 → 클라이언트 제어 메시지 종류. <b>프론트와의 계약</b>이라 이름을 바꾸면 클라이언트가 깨진다.
     */
    private enum MessageType {
        /** 서버 준비 완료. 프론트는 이 신호 이후에 오디오를 보낸다(그전 프레임은 버려진다). */
        CALL_READY,
        /** 정상 종료 통지({@code callId}/{@code reason}/{@code callTime}). 뒤이어 소켓이 NORMAL로 닫힌다. */
        CALL_ENDED,
        /** 끼어들기 — <b>이미 보낸</b> AI 음성의 재생을 멈추라는 신호({@code callId}). 통화는 계속된다. */
        AI_SPEECH_CANCELED,
        /** 서버 주도 종료 직전 통지. 뒤이어 소켓이 닫힌다. */
        ERROR
    }

    /**
     * 진행 중인 통화 하나가 들고 있는 것 전부. 수명이 같아 한 홀더로 묶었다 — 정리를 한 번에 하기 위함.
     * 통화 스코프 상태(전사 버퍼·LLM 컨텍스트 등)가 늘면 평행 맵을 만들지 말고 여기 필드로 붙인다.
     *
     * @param session 이 통화의 WebSocket 세션. REST 종료가 callId로 소켓을 찾아 닫아야 해서 들고 있는다.
     * @param worker  통화당 단일 스레드 — 제출 순서 = 실행 순서(AI 응답 순서 보장).
     * @param ticket  핸드셰이크에서 검증된 신원(callId/relationshipId/characterId). AI 배선·전사 저장의 기준.
     * @param history 세션 스코프 대화 이력. <b>워커 스레드만</b> 읽고 쓴다(스레드 confine → 동기화 불필요).
     * @param turnGate 끼어들기 판정. history와 달리 <b>두 스레드가 만진다</b>(워커가 열고, gRPC 콜백이 취소).
     * @param recorder 통화 녹음(타임라인 믹스). 세 스레드가 쓰지만 내부에서 직렬화한다.
     * @param voice   이 통화 AI의 목소리. 연결 시 1회 해석해 얼려둔다 — 문장마다 다시 조회하지 않기 위함이자,
     *                통화 도중 캐릭터 이미지가 바뀌어도 말하던 목소리가 중간에 갈리지 않게 하기 위함이다.
     */
    private record ActiveCall(WebSocketSession session, SttStream stream, ExecutorService worker,
                              WsTicket ticket, List<AiChatHistoryItem> history, TurnGate turnGate,
                              CallRecorder recorder, TTSVoice voice) {
    }

    /**
     * 통화 한 건의 <b>턴 취소 게이트</b>(끼어들기). 워커가 AI 턴을 시작할 때 번호를 받아두고, 사용자가 말을
     * 시작하면 gRPC 콜백이 "지금 턴까지 취소"로 표시한다. 워커는 자기 턴이 취소됐는지만 확인하면 된다.
     *
     * <p><b>왜 플래그 하나가 아니라 번호인가</b>: 취소 표시가 다음 턴까지 살아 있으면 사용자가 끼어들어 만든
     * <b>바로 그 다음 턴</b>이 시작하자마자 취소된다(끼어든 발화에 AI가 영영 답하지 못한다). 턴 번호로 비교하면
     * 취소는 그 시점에 열려 있던 턴에만 적용된다.
     */
    private static final class TurnGate {

        private final AtomicLong current = new AtomicLong();
        private final AtomicLong canceled = new AtomicLong();
        /** 오디오가 실제로 소켓으로 나간 최신 턴. 나간 게 없으면 클라이언트가 비울 큐도 없다. */
        private final AtomicLong spoken = new AtomicLong();
        /** 재생 중단을 통지한 최신 턴. partial은 말하는 내내 도착하므로 턴당 1회로 줄이는 기준이다. */
        private final AtomicLong notified = new AtomicLong();

        /** 새 턴을 열고 그 번호를 준다. 워커(통화당 단일 스레드)만 호출한다. */
        long begin() {
            return current.incrementAndGet();
        }

        /** 이 턴의 오디오가 소켓으로 나갔다고 표시한다. 워커만 호출한다. */
        void markSpoken(long turn) {
            spoken.accumulateAndGet(turn, Math::max);
        }

        /**
         * 지금 열려 있는 턴을 취소로 표시한다. gRPC 콜백이 호출 — 블로킹 없이 값만 쓴다.
         * <p>진행 중인 턴이 없어도 무해하다(직전 턴 번호를 찍을 뿐, 다음 턴은 번호가 더 커서 안 걸린다).
         *
         * @return 클라이언트에 <b>재생 중단을 통지해야</b> 하면 true. 이 턴 오디오가 이미 나갔고({@code spoken})
         *         아직 통지하지 않았을 때({@code notified})뿐이다 — 두 조건이 각각 왜 필요한지는 그 필드 주석에 있다.
         */
        boolean cancelCurrent() {
            long turn = current.get();
            canceled.set(turn);
            if (spoken.get() < turn) {
                return false;
            }
            return notified.getAndAccumulate(turn, Math::max) < turn;
        }

        /**
         * ⚠ 드물게 {@link #begin}과 겹치면 직전 턴 번호가 찍혀 이번 턴이 안 끊길 수 있다. partial은 말하는
         * 동안 계속 도착하므로 바로 다음 프레임이 취소한다 — 재시도 로직을 넣을 이유가 없다.
         */
        boolean isCanceled(long turn) {
            return canceled.get() >= turn;
        }
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

    /** CLOVA 인식 결과 콜백. final은 AI 턴을 열고, partial은 끼어들기를 건다. 에러 시 WebSocket을 닫는다. */
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
                } else if (hasSpeech(result)) {
                    // partial = 사용자가 지금 말하는 중 → 진행 중인 AI 턴을 끊는다(끼어들기).
                    cancelSpeakingTurn(sessionId);
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
