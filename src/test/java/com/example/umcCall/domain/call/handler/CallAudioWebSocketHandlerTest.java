package com.example.umcCall.domain.call.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.umcCall.domain.call.client.ClovaSpeechClient;
import com.example.umcCall.domain.call.client.ClovaSpeechProperties;
import com.example.umcCall.domain.call.client.ClovaVoiceClient;
import com.example.umcCall.domain.call.enums.CallEndReason;
import com.example.umcCall.domain.call.enums.CallSpeaker;
import com.example.umcCall.domain.call.event.CallEndedEvent;
import com.example.umcCall.domain.call.recording.CallRecordingService;
import com.example.umcCall.domain.call.recording.WavCodec;
import com.example.umcCall.domain.call.service.CallArtifactRegistry;
import com.example.umcCall.domain.call.service.CallConversationService;
import com.example.umcCall.domain.call.service.CallHistoryService;
import com.example.umcCall.domain.call.service.CallService;
import com.example.umcCall.domain.call.service.CallSummaryService;
import com.example.umcCall.domain.call.service.CallVoiceResolver;
import com.example.umcCall.domain.call.ticket.WsTicket;
import com.example.umcCall.domain.image.enums.TTSVoice;
import com.example.umcCall.domain.call.ticket.WsTicketHandshakeInterceptor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nbp.cdncp.nest.grpc.proto.v1.NestResponse;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * 서버 → 클라이언트 제어 메시지의 <b>JSON 모양을 고정</b>하는 계약 테스트.
 * 프론트가 이 모양대로 파서를 짜므로, 여기가 깨지면 클라이언트가 깨진다.
 */
@ExtendWith(MockitoExtension.class)
class CallAudioWebSocketHandlerTest {

    private static final Long CALL_ID = 7L;
    private static final Long RELATIONSHIP_ID = 30L;
    private static final Long CHARACTER_ID = 20L;
    /** 이 캐릭터의 목소리. 기본값(defaultFor)과 <b>다른</b> 값이라야 "해석 결과를 쓴다"가 증명된다. */
    private static final TTSVoice CHARACTER_VOICE = TTSVoice.YEJI;

    @Mock private ClovaSpeechClient clovaSpeechClient;
    @Mock private ClovaVoiceClient clovaVoiceClient;
    @Mock private CallConversationService callConversationService;
    @Mock private CallService callService;
    @Mock private CallHistoryService callHistoryService;
    @Mock private CallRecordingService callRecordingService;
    @Mock private CallSummaryService callSummaryService;
    @Mock private CallVoiceResolver callVoiceResolver;
    @Mock private CallArtifactRegistry callArtifactRegistry;

    /** "이만큼도 안 걸린다"의 기준. 정리 경로는 어디서도 산출물을 기다리지 않아야 한다. */
    private static final long ARTIFACT_WAIT_MS = 2000;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private CallAudioWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        lenient().when(callVoiceResolver.resolve(CHARACTER_ID)).thenReturn(CHARACTER_VOICE);
        lenient().when(callRecordingService.save(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        lenient().when(callSummaryService.generate(any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        handler = new CallAudioWebSocketHandler(
                clovaSpeechClient, clovaVoiceClient, callConversationService, callService,
                callHistoryService,
                new ClovaSpeechProperties("clovaspeech-gw.ncloud.com", 50051, "secret", 700),
                callRecordingService, callSummaryService, callVoiceResolver,
                callArtifactRegistry, objectMapper);
    }

    /** 핸드셰이크 인터셉터가 신원을 실어 둔 열린 세션. */
    private WebSocketSession openSession() {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(WsTicketHandshakeInterceptor.WS_TICKET_ATTRIBUTE,
                new WsTicket(CALL_ID, RELATIONSHIP_ID, CHARACTER_ID));
        lenient().when(session.getId()).thenReturn("session-1");
        lenient().when(session.getAttributes()).thenReturn(attributes);
        lenient().when(session.isOpen()).thenReturn(true);
        return session;
    }

    @SuppressWarnings("unchecked")
    private void givenSttStreamOpens() {
        when(clovaSpeechClient.openRecognizeStream(any()))
                .thenReturn(mock(StreamObserver.class));
    }

    /** 세션에 나간 첫 텍스트 프레임을 JSON으로 파싱해 돌려준다. */
    private JsonNode captureControlMessage(WebSocketSession session) throws Exception {
        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());
        return objectMapper.readTree(captor.getValue().getPayload());
    }

    /** 마지막으로 나간 텍스트 프레임. 연결이 선 뒤라 앞에 CALL_READY가 이미 나가 있다. */
    private JsonNode captureLastControlMessage(WebSocketSession session) throws Exception {
        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, atLeastOnce()).sendMessage(captor.capture());
        List<TextMessage> sent = captor.getAllValues();
        return objectMapper.readTree(sent.get(sent.size() - 1).getPayload());
    }

    /**
     * 해당 {@code type}의 제어 프레임 매처. 순서 검증에서 앞서 나간 CALL_READY와 구별하려고 쓴다.
     */
    private static TextMessage controlOfType(String type) {
        return argThat(message ->
                message != null && message.getPayload().contains("\"type\":\"" + type + "\""));
    }

    /** CALL_READY까지 끝난 통화 하나를 세워 둔다. */
    private WebSocketSession givenConnectedCall() {
        WebSocketSession session = openSession();
        givenSttStreamOpens();
        handler.afterConnectionEstablished(session);
        return session;
    }

    @Test
    void 연결이_준비되면_CALL_READY를_보낸다() throws Exception {
        WebSocketSession session = openSession();
        givenSttStreamOpens();

        handler.afterConnectionEstablished(session);

        JsonNode message = captureControlMessage(session);
        assertThat(message.get("type").asText()).isEqualTo("CALL_READY");
        assertThat(message.get("data").get("callId").asLong()).isEqualTo(CALL_ID);
    }

    @Test
    void CALL_READY는_연결_상태_전이가_성공한_뒤에_나간다() throws Exception {
        WebSocketSession session = openSession();
        givenSttStreamOpens();
        // 스위퍼가 먼저 마감한 통화 — 곧 닫힐 소켓에 "준비됨"을 보내면 안 된다.
        doThrow(new IllegalStateException("이미 종료된 통화")).when(callService).connect(CALL_ID);

        handler.afterConnectionEstablished(session);

        JsonNode message = captureControlMessage(session);
        assertThat(message.get("type").asText()).isEqualTo("ERROR");
        // 통지가 close보다 먼저여야 한다 — 뒤집히면 isOpen 가드에 걸려 사유 없이 닫힌다.
        InOrder inOrder = inOrder(session);
        inOrder.verify(session).sendMessage(controlOfType("ERROR"));
        inOrder.verify(session).close(CloseStatus.SERVER_ERROR);
    }

    @Test
    void 서버_주도_종료는_ERROR_봉투로_통지한다() throws Exception {
        WebSocketSession session = openSession();
        // 스트림 개설 자체가 실패한 경로.
        when(clovaSpeechClient.openRecognizeStream(any()))
                .thenThrow(new IllegalStateException("CLOVA 연결 실패"));

        handler.afterConnectionEstablished(session);

        JsonNode message = captureControlMessage(session);
        assertThat(message.get("type").asText()).isEqualTo("ERROR");
        assertThat(message.get("data").get("reason").asText()).isEqualTo("SERVER_ERROR");
        InOrder inOrder = inOrder(session);
        inOrder.verify(session).sendMessage(controlOfType("ERROR"));
        inOrder.verify(session).close(CloseStatus.SERVER_ERROR);
    }

    @Test
    void 통화가_마감되면_CALL_ENDED로_사유와_통화시간을_통지한다() throws Exception {
        WebSocketSession session = givenConnectedCall();

        handler.onCallEnded(new CallEndedEvent(CALL_ID, CallEndReason.USER_ENDED, 42));

        JsonNode message = captureLastControlMessage(session);
        assertThat(message.get("type").asText()).isEqualTo("CALL_ENDED");
        assertThat(message.get("data").get("callId").asLong()).isEqualTo(CALL_ID);
        assertThat(message.get("data").get("reason").asText()).isEqualTo("USER_ENDED");
        // 서버 계산값이라 프론트 자체 측정과 어긋나지 않는다(REST CallEndResponse.callTime과 같은 값).
        assertThat(message.get("data").get("callTime").asInt()).isEqualTo(42);
        // 정상 종료다 — ERROR로 닫으면 프론트가 사고로 표시한다.
        // 순서까지 본다: close가 먼저 가면 sendControl의 isOpen 가드에 걸려 통지가 통째로 사라진다.
        InOrder inOrder = inOrder(session);
        inOrder.verify(session).sendMessage(controlOfType("CALL_ENDED"));
        inOrder.verify(session).close(CloseStatus.NORMAL);
    }

    // --- 종료 시 산출물 준비 (녹음·요약) ---------------------------------------------------------

    @Test
    void 통화_종료_응답은_산출물을_기다리지_않는다() {
        // ⚠ 이 리스너는 PATCH /calls/{id}/end의 요청 스레드에서 응답 직전에 돈다 —
        // 여기서 기다리면 사용자가 끊기를 눌렀는데 화면이 그만큼 멈춘다.
        // 기다리는 건 프론트의 종료 화면 조회(GET /calls/{callId}?wait=true)가 맡는다.
        WebSocketSession session = givenConnectedCall();
        handler.handleBinaryMessage(session, new BinaryMessage(ByteBuffer.wrap(pcm((short) 100, 160))));
        // 영원히 안 끝나는 산출물 — S3·AI가 hang한 상황을 흉내낸다.
        when(callRecordingService.save(any(), any())).thenReturn(new CompletableFuture<>());
        when(callSummaryService.generate(CALL_ID)).thenReturn(new CompletableFuture<>());

        long startedAt = System.nanoTime();
        handler.onCallEnded(new CallEndedEvent(CALL_ID, CallEndReason.USER_ENDED, 42));
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;

        assertThat(elapsedMs).isLessThan(ARTIFACT_WAIT_MS);
        // 그래도 기다릴 수 있게 등록은 해 둔다.
        verify(callArtifactRegistry).register(eq(CALL_ID), any());
    }

    @Test
    void 종료되면_녹음과_요약을_모두_시작한다() {
        givenConnectedCall();

        handler.onCallEnded(new CallEndedEvent(CALL_ID, CallEndReason.USER_ENDED, 42));

        // 녹음은 소리가 있어야 저장되므로(이 통화는 무음) save는 안 불릴 수 있지만, 요약은 항상 시도한다
        // — 전사가 없으면 요약 서비스가 스스로 NONE으로 끝낸다.
        verify(callSummaryService).generate(CALL_ID);
    }

    @Test
    void 클라이언트가_먼저_끊은_경로도_산출물을_레지스트리에_등록한다() {
        // ⚠ 이 경로가 등록의 존재 이유다 — 기다려 줄 요청 스레드가 없어서 종료 시점엔 아무도 못 기다린다.
        // 등록해 두면 나중에 프론트가 종료 화면에서 조회(?wait=true)로 붙어 기다릴 수 있다.
        // 다른 종료 경로도 전부 finishArtifacts를 지나므로 여기가 서면 나머지도 선다.
        WebSocketSession session = givenConnectedCall();

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        verify(callArtifactRegistry).register(eq(CALL_ID), any());
    }

    @Test
    void 클라이언트가_먼저_끊은_경로는_산출물을_기다리지_않는다() {
        // ⚠ 정리 경로는 onCallEnded 말고도 여럿이 공유한다(소켓 종료·WS 수신 스레드·gRPC 오류·앱 종료).
        // 거기서도 기다리면 기다려 줄 요청도 없이 그 스레드만 묶인다 — 대기는 onCallEnded에서만이다.
        WebSocketSession session = givenConnectedCall();
        when(callSummaryService.generate(CALL_ID)).thenReturn(new CompletableFuture<>());

        long startedAt = System.nanoTime();
        handler.afterConnectionClosed(session, CloseStatus.NORMAL);
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;

        // 산출물이 영원히 안 끝나는데도 즉시 돌아와야 한다.
        assertThat(elapsedMs).isLessThan(ARTIFACT_WAIT_MS);
        // 그래도 시작은 한다 — 이 통화는 나중에 조회될 때 PROCESSING일 수 있다.
        verify(callSummaryService).generate(CALL_ID);
    }

    @Test
    void 시간_상한_마감은_TIMEOUT_사유로_나간다() throws Exception {
        WebSocketSession session = givenConnectedCall();

        // 사유가 갈려야 프론트가 "통화 종료"와 "시간 초과"를 다르게 안내할 수 있다.
        handler.onCallEnded(new CallEndedEvent(CALL_ID, CallEndReason.TIMEOUT, 3600));

        JsonNode message = captureLastControlMessage(session);
        assertThat(message.get("data").get("reason").asText()).isEqualTo("TIMEOUT");
    }

    @Test
    void 다른_통화의_마감은_이_세션을_건드리지_않는다() throws Exception {
        WebSocketSession session = givenConnectedCall();

        handler.onCallEnded(new CallEndedEvent(CALL_ID + 1, CallEndReason.USER_ENDED, 10));

        // CALL_READY 하나뿐 — 세션 탐색이 callId로 정확히 걸러져야 한다.
        verify(session, times(1)).sendMessage(any(TextMessage.class));
        verify(session, never()).close(any());
    }

    @Test
    void 앱_종료_시엔_CALL_ENDED를_보내지_않는다() throws Exception {
        WebSocketSession session = givenConnectedCall();

        handler.closeActiveCallsOnShutdown();

        // 마감을 여기서 하므로 callTime을 알 수 없고, 곧 서버가 죽어 통지가 쓸모없다.
        verify(session, times(1)).sendMessage(any(TextMessage.class)); // CALL_READY 하나뿐
    }

    @Test
    void 앱_종료_시_진행_중인_통화를_마감한다() throws Exception {
        WebSocketSession session = givenConnectedCall();

        handler.closeActiveCallsOnShutdown();

        // 컨테이너가 소켓을 닫을 땐 DataSource가 이미 내려가 finish()가 실패한다 — 여기서 마감해야 한다.
        verify(callService).finish(CALL_ID);
        verify(session).close(CloseStatus.NORMAL);
    }

    @Test
    void 진행_중인_통화가_없으면_종료_시_아무것도_하지_않는다() {
        handler.closeActiveCallsOnShutdown();

        verifyNoInteractions(callService);
    }

    @Test
    void 오디오는_등록_전에_도착하면_버려진다() {
        WebSocketSession session = openSession();

        // afterConnectionEstablished 전 = activeCalls에 없음. CALL_READY가 필요한 이유 그 자체다.
        handler.handleBinaryMessage(session, new BinaryMessage(new byte[] {1, 2, 3}));

        verify(clovaSpeechClient, never()).openRecognizeStream(any());
    }

    // --- 끼어들기(barge-in) — 아직 전송 전인 AI 대사를 폐기하는 서버 측 취소 -------------------

    /** 핸들러가 CLOVA에 넘긴 인식 결과 콜백. 이걸로 partial/final 도착을 재현한다. */
    @SuppressWarnings("unchecked")
    private StreamObserver<NestResponse> captureSttObserver() {
        ArgumentCaptor<StreamObserver<NestResponse>> captor =
                ArgumentCaptor.forClass(StreamObserver.class);
        verify(clovaSpeechClient).openRecognizeStream(captor.capture());
        return captor.getValue();
    }

    private static NestResponse sttResult(String text, String epdType) {
        return NestResponse.newBuilder()
                .setContents("{\"responseType\":[\"transcription\"],\"transcription\":{\"text\":\""
                        + text + "\",\"epdType\":\"" + epdType + "\"}}")
                .build();
    }

    /** 턴 끝(침묵) = final. AI 턴을 연다. */
    private static NestResponse finalResult(String text) {
        return sttResult(text, "gap");
    }

    /** 발화 중간 = partial. "사용자가 지금 말하는 중" 신호이자 끼어들기 트리거다. */
    private static NestResponse partialResult(String text) {
        return sttResult(text, "durationThreshold");
    }

    /** AI가 대사를 조각(SSE chunk)으로 흘려보내는 상황을 재현한다. */
    @SuppressWarnings("unchecked")
    private void givenAiStreams(String... chunks) {
        doAnswer(invocation -> {
            Consumer<String> onChunk = invocation.getArgument(3);
            for (String chunk : chunks) {
                onChunk.accept(chunk);
            }
            return null;
        }).when(callConversationService).respondStream(any(), any(), any(), any());
    }

    @Test
    void 끼어들면_아직_전송_전인_AI_대사는_폐기된다() throws Exception {
        WebSocketSession session = givenConnectedCall();
        StreamObserver<NestResponse> stt = captureSttObserver();
        CountDownLatch replyStreamed = new CountDownLatch(1);

        // 첫 문장이 완성되기 전에 사용자가 말을 시작한 상황.
        doAnswer(invocation -> {
            stt.onNext(partialResult("아니 잠깐만"));
            Consumer<String> onChunk = invocation.getArgument(3);
            try {
                // 취소된 턴은 조각 콜백이 예외로 스트림을 접는다 — 실제 경로에서도 여기서 빠져나간다.
                onChunk.accept("AI가 하려던 말이야.");
            } finally {
                replyStreamed.countDown();
            }
            return null;
        }).when(callConversationService).respondStream(any(), any(), any(), any());

        stt.onNext(finalResult("안녕"));

        assertThat(replyStreamed.await(2, TimeUnit.SECONDS)).isTrue();
        // 합성 자체를 건너뛴다 — 어차피 못 내보낼 대사라 TTS 호출·비용이 통째로 낭비다.
        verify(clovaVoiceClient, after(300).never()).synthesize(any(), any());
        verify(session, never()).sendMessage(any(BinaryMessage.class));
        // 안 들린 대사는 이력·전사에 남기지 않는다(사용자 발화만 남는다).
        verify(callHistoryService, never()).appendHistory(any(), eq(CallSpeaker.AI), any());
    }

    @Test
    void 끼어들지_않으면_AI_대사가_그대로_나간다() throws Exception {
        WebSocketSession session = givenConnectedCall();
        StreamObserver<NestResponse> stt = captureSttObserver();
        givenAiStreams("정상 대사");
        when(clovaVoiceClient.synthesize(any(), any())).thenReturn(new byte[] {1, 2, 3});

        stt.onNext(finalResult("안녕"));

        // 대조군: 취소 게이트가 정상 턴을 잡아먹지 않는지 본다.
        verify(session, timeout(2000)).sendMessage(any(BinaryMessage.class));
        verify(callHistoryService, timeout(2000)).appendHistory(CALL_ID, CallSpeaker.AI, "정상 대사");
    }

    @Test
    void 끼어들어_만들어진_다음_턴은_취소되지_않는다() {
        // ⚠ 취소를 boolean 플래그 하나로 두면 이 테스트가 깨진다 — 끼어든 발화가 만든 바로 그 다음 턴이
        // 시작하자마자 취소돼 AI가 영영 답하지 못한다. 턴 번호로 비교하는 이유가 이것이다.
        WebSocketSession session = givenConnectedCall();
        StreamObserver<NestResponse> stt = captureSttObserver();
        doAnswer(invocation -> {
            stt.onNext(partialResult("아니"));   // 턴1 도중 끼어듦
            Consumer<String> onChunk = invocation.getArgument(3);
            onChunk.accept("첫째 대사");
            return null;
        }).doAnswer(invocation -> {
            Consumer<String> onChunk = invocation.getArgument(3);
            onChunk.accept("둘째 대사");
            return null;
        }).when(callConversationService).respondStream(any(), any(), any(), any());
        when(clovaVoiceClient.synthesize(any(), any())).thenReturn(new byte[] {1});

        stt.onNext(finalResult("안녕"));         // 턴1 — 끼어들기로 폐기된다
        stt.onNext(finalResult("아니 잠깐만"));   // 턴2 — 끼어든 발화 자체가 만든 턴

        verify(clovaVoiceClient, timeout(2000)).synthesize(eq("둘째 대사"), any());
        verify(clovaVoiceClient, never()).synthesize(eq("첫째 대사"), any());
    }

    @Test
    void 빈_partial은_끼어들기로_치지_않는다() throws Exception {
        // ⚠ 빈 결과가 취소로 이어지면 AI가 영영 말을 못 하고, 로그엔 "끼어들기"만 찍혀 추적이 어렵다.
        // 지금은 CLOVA CONFIG의 skipEmptyText가 막아주지만 설정 한 줄에 기대지 않는다.
        WebSocketSession session = givenConnectedCall();
        StreamObserver<NestResponse> stt = captureSttObserver();
        doAnswer(invocation -> {
            stt.onNext(partialResult(""));    // 무음 구간에서 빈 결과가 흘러온 상황
            stt.onNext(partialResult("  "));
            Consumer<String> onChunk = invocation.getArgument(3);
            onChunk.accept("정상 대사");
            return null;
        }).when(callConversationService).respondStream(any(), any(), any(), any());
        when(clovaVoiceClient.synthesize(any(), any())).thenReturn(new byte[] {1});

        stt.onNext(finalResult("안녕"));

        verify(session, timeout(2000)).sendMessage(any(BinaryMessage.class));
        verify(callHistoryService, timeout(2000)).appendHistory(CALL_ID, CallSpeaker.AI, "정상 대사");
    }

    // --- 문장 단위 스트리밍 송신 (TTFA) ------------------------------------------------------

    @Test
    void 문장이_완성될_때마다_따로_송신한다() throws Exception {
        // 대사 전체를 기다리지 않고 첫 문장부터 내보내는 게 TTFA 단축의 전부다.
        WebSocketSession session = givenConnectedCall();
        StreamObserver<NestResponse> stt = captureSttObserver();
        givenAiStreams("응, 나 방금 퇴근했어. ", "너는 뭐 하고 있었어?");
        when(clovaVoiceClient.synthesize(any(), any())).thenReturn(new byte[] {1});

        stt.onNext(finalResult("뭐 해?"));

        // wav가 문장 수만큼 나간다 — 프론트는 도착 순서대로 이어 재생한다.
        verify(session, timeout(2000).times(2)).sendMessage(any(BinaryMessage.class));
        verify(clovaVoiceClient, timeout(2000)).synthesize(eq("응, 나 방금 퇴근했어."), any());
        verify(clovaVoiceClient, timeout(2000)).synthesize(eq("너는 뭐 하고 있었어?"), any());
        // 전사·이력은 턴당 한 줄로 합친다(문장별로 쪼개면 통화 전문 화면이 잘게 갈라진다).
        verify(callHistoryService, timeout(2000))
                .appendHistory(CALL_ID, CallSpeaker.AI, "응, 나 방금 퇴근했어. 너는 뭐 하고 있었어?");
    }

    // --- 캐릭터별 음성 --------------------------------------------------------------------------

    @Test
    void 캐릭터에_매핑된_목소리로_합성한다() throws Exception {
        givenConnectedCall();
        StreamObserver<NestResponse> stt = captureSttObserver();
        givenAiStreams("응, 나 여기 있어.");
        when(clovaVoiceClient.synthesize(any(), any())).thenReturn(new byte[] {1});

        stt.onNext(finalResult("어디야?"));

        // enum 이름이 아니라 CLOVA 화자 ID가 나가야 한다.
        verify(clovaVoiceClient, timeout(2000)).synthesize(any(), eq(CHARACTER_VOICE.speakerId()));
    }

    @Test
    void 화자는_연결_시_한_번만_조회한다() throws Exception {
        // ⚠ TTS는 문장 수만큼 불린다 — 합성할 때마다 해석하면 문장 하나에 DB가 두 방씩 나간다.
        givenConnectedCall();
        StreamObserver<NestResponse> stt = captureSttObserver();
        givenAiStreams("응, 나 방금 퇴근했어. ", "너는 뭐 하고 있었어?");
        when(clovaVoiceClient.synthesize(any(), any())).thenReturn(new byte[] {1});

        stt.onNext(finalResult("뭐 해?"));
        stt.onNext(finalResult("그렇구나?"));

        verify(clovaVoiceClient, timeout(2000).atLeast(3)).synthesize(any(), any());
        verify(callVoiceResolver, times(1)).resolve(CHARACTER_ID);
    }

    @Test
    void 이모지만_남은_꼬리는_합성하지_않고_턴도_끊지_않는다() throws Exception {
        // ⚠ CLOVA는 발음할 게 없는 텍스트에 400을 준다("TN result is empty").
        // AI 대사가 이모지로 끝나면 flush 꼬리가 딱 이 모양이라, 안 거르면 매 턴 오류로 끊긴다.
        WebSocketSession session = givenConnectedCall();
        StreamObserver<NestResponse> stt = captureSttObserver();
        givenAiStreams("응, 오늘은 그냥 집에 있었어. ", "🙂");
        when(clovaVoiceClient.synthesize(any(), any())).thenReturn(new byte[] {1});

        stt.onNext(finalResult("뭐 해?"));

        verify(clovaVoiceClient, timeout(2000)).synthesize(eq("응, 오늘은 그냥 집에 있었어."), any());
        verify(clovaVoiceClient, never()).synthesize(eq("🙂"), any());
        // 이모지는 소리로 안 나갔으니 이력에도 안 남는다.
        verify(callHistoryService, timeout(2000))
                .appendHistory(CALL_ID, CallSpeaker.AI, "응, 오늘은 그냥 집에 있었어.");
    }

    @Test
    void 첫_문장을_말한_뒤_끼어들면_말한_부분까지만_남는다() throws Exception {
        WebSocketSession session = givenConnectedCall();
        StreamObserver<NestResponse> stt = captureSttObserver();
        doAnswer(invocation -> {
            Consumer<String> onChunk = invocation.getArgument(3);
            onChunk.accept("응, 나 방금 퇴근했어. ");   // 이 문장은 이미 나갔다 = 사용자가 들었다
            stt.onNext(partialResult("아 맞다"));      // 듣고 있다가 끼어듦
            onChunk.accept("너는 뭐 하고 있었어?");     // 이 문장은 나가면 안 된다
            return null;
        }).when(callConversationService).respondStream(any(), any(), any(), any());
        when(clovaVoiceClient.synthesize(any(), any())).thenReturn(new byte[] {1});

        stt.onNext(finalResult("뭐 해?"));

        verify(session, timeout(2000).times(1)).sendMessage(any(BinaryMessage.class));
        verify(clovaVoiceClient, never()).synthesize(eq("너는 뭐 하고 있었어?"), any());
        // 들린 문장은 남긴다 — 안 들린 대사만 빼는 게 원칙이다(둘 다 빼면 AI가 자기 말을 잊는다).
        verify(callHistoryService, timeout(2000))
                .appendHistory(CALL_ID, CallSpeaker.AI, "응, 나 방금 퇴근했어.");
    }

    // --- 끼어들기 2단계: 이미 보낸 wav의 재생 중단 통지 -----------------------------------------

    /** 세션에 나간 제어 프레임 중 해당 {@code type}인 것들. 같이 나간 오디오는 captor가 타입으로 걸러낸다. */
    private List<JsonNode> controlsOfType(WebSocketSession session, String type) throws Exception {
        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, atLeast(0)).sendMessage(captor.capture());
        List<JsonNode> found = new ArrayList<>();
        for (TextMessage message : captor.getAllValues()) {
            JsonNode node = objectMapper.readTree(message.getPayload());
            if (type.equals(node.get("type").asText())) {
                found.add(node);
            }
        }
        return found;
    }

    /**
     * 통지는 전용 스레드로 나가므로 도착을 기다린다. 하나가 온 뒤에도 잠깐 더 기다리는 건
     * <b>중복 통지가 있으면 그 사이에 도착하게</b> 하려는 것이다(단일 스레드라 곧바로 뒤따라온다).
     */
    private List<JsonNode> awaitControlsOfType(WebSocketSession session, String type) throws Exception {
        long deadline = System.currentTimeMillis() + 2000;
        while (controlsOfType(session, type).isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        Thread.sleep(100);
        return controlsOfType(session, type);
    }

    @Test
    void 이미_보낸_음성이_있으면_끼어들_때_재생_중단을_통지한다() throws Exception {
        // 서버는 내보낸 프레임을 회수할 수 없다 — 재생 큐를 비우는 건 클라이언트 몫이라 통지가 필요하다.
        WebSocketSession session = givenConnectedCall();
        StreamObserver<NestResponse> stt = captureSttObserver();
        doAnswer(invocation -> {
            Consumer<String> onChunk = invocation.getArgument(3);
            onChunk.accept("응, 나 방금 퇴근했어. ");   // 이 wav는 이미 나갔다 = 회수 불가 구간
            stt.onNext(partialResult("아 맞다"));       // 재생 도중 끼어듦
            onChunk.accept("너는 뭐 하고 있었어?");
            return null;
        }).when(callConversationService).respondStream(any(), any(), any(), any());
        when(clovaVoiceClient.synthesize(any(), any())).thenReturn(new byte[] {1});

        stt.onNext(finalResult("뭐 해?"));

        List<JsonNode> canceled = awaitControlsOfType(session, "AI_SPEECH_CANCELED");
        assertThat(canceled).hasSize(1);
        assertThat(canceled.get(0).get("data").get("callId").asLong()).isEqualTo(CALL_ID);
        // 종료 통지가 아니다 — 통화는 그대로 이어진다.
        verify(session, never()).close(any());
    }

    @Test
    void 말하는_동안_partial이_계속_와도_통지는_한_번뿐이다() throws Exception {
        // ⚠ partial은 말하는 내내 수십 번 온다. 턴당 1회로 줄이지 않으면 제어 프레임이 폭주한다.
        WebSocketSession session = givenConnectedCall();
        StreamObserver<NestResponse> stt = captureSttObserver();
        doAnswer(invocation -> {
            Consumer<String> onChunk = invocation.getArgument(3);
            onChunk.accept("응, 나 방금 퇴근했어. ");
            stt.onNext(partialResult("아"));
            stt.onNext(partialResult("아 맞다"));
            stt.onNext(partialResult("아 맞다 그거"));
            onChunk.accept("너는 뭐 하고 있었어?");
            return null;
        }).when(callConversationService).respondStream(any(), any(), any(), any());
        when(clovaVoiceClient.synthesize(any(), any())).thenReturn(new byte[] {1});

        stt.onNext(finalResult("뭐 해?"));

        assertThat(awaitControlsOfType(session, "AI_SPEECH_CANCELED")).hasSize(1);
    }

    @Test
    void 아직_아무것도_안_보냈으면_재생_중단을_통지하지_않는다() throws Exception {
        // 1단계(서버 측 취소)로 이미 막힌 턴이다 — 클라이언트엔 비울 큐가 없으니 통지할 이유도 없다.
        WebSocketSession session = givenConnectedCall();
        StreamObserver<NestResponse> stt = captureSttObserver();
        CountDownLatch replyStreamed = new CountDownLatch(1);
        doAnswer(invocation -> {
            stt.onNext(partialResult("아니 잠깐만"));   // 첫 문장이 나가기 전에 끼어듦
            Consumer<String> onChunk = invocation.getArgument(3);
            try {
                onChunk.accept("AI가 하려던 말이야.");
            } finally {
                replyStreamed.countDown();
            }
            return null;
        }).when(callConversationService).respondStream(any(), any(), any(), any());

        stt.onNext(finalResult("안녕"));

        assertThat(replyStreamed.await(2, TimeUnit.SECONDS)).isTrue();
        verify(session, after(300).never()).sendMessage(any(BinaryMessage.class)); // 나간 오디오가 없다
        assertThat(controlsOfType(session, "AI_SPEECH_CANCELED")).isEmpty();
    }

    // --- 통화 녹음 배선 -------------------------------------------------------------------

    /** 5초짜리 사용자 업스트림(16kHz raw PCM). AI 조각이 이 구간 안에 확실히 들어오도록 길게 잡는다. */
    private static byte[] pcm(short value, int samples) {
        ByteBuffer buffer = ByteBuffer.allocate(samples * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < samples; i++) {
            buffer.putShort(value);
        }
        return buffer.array();
    }

    /** CLOVA Voice가 주는 모양의 wav(24kHz 모노 16-bit). */
    private static byte[] aiWav(short value, int samples) {
        byte[] header = WavCodec.header(samples * 2, 24_000);
        return ByteBuffer.allocate(header.length + samples * 2)
                .put(header).put(pcm(value, samples)).array();
    }

    /** 통화 마감 때 보관으로 넘어간 녹음의 샘플 전체. */
    private short[] recordedSamples() {
        ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
        verify(callRecordingService).save(eq(CALL_ID), captor.capture());
        return WavCodec.decode(captor.getValue()).samples();
    }

    @Test
    void 통화가_끝나면_두_목소리가_섞인_녹음이_보관된다() throws Exception {
        WebSocketSession session = givenConnectedCall();
        StreamObserver<NestResponse> stt = captureSttObserver();
        givenAiStreams("응, 나 방금 퇴근했어.");
        when(clovaVoiceClient.synthesize(any(), any())).thenReturn(aiWav((short) 3000, 24_000));

        handler.handleBinaryMessage(session, new BinaryMessage(pcm((short) 1000, 80_000)));
        stt.onNext(finalResult("뭐 해?"));
        verify(session, timeout(2000)).sendMessage(any(BinaryMessage.class));

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        // 사용자 5초가 통째로 담겼고(침묵 포함), AI 조각이 그 위 어딘가에 얹혀 섞였다.
        short[] samples = recordedSamples();
        assertThat(samples.length).isGreaterThanOrEqualTo(80_000);
        assertThat(samples).contains((short) 4000);
    }

    @Test
    void 전송하지_못한_AI_음성은_녹음에도_남지_않는다() throws Exception {
        // 녹음 기준은 이력·전사와 같다 — 실제로 내보낸 소리만 남긴다.
        WebSocketSession session = givenConnectedCall();
        StreamObserver<NestResponse> stt = captureSttObserver();
        givenAiStreams("이 대사는 전송에 실패한다.");
        when(clovaVoiceClient.synthesize(any(), any())).thenReturn(aiWav((short) 3000, 24_000));
        doThrow(new IOException("소켓 끊김")).when(session).sendMessage(any(BinaryMessage.class));

        handler.handleBinaryMessage(session, new BinaryMessage(pcm((short) 1000, 80_000)));
        stt.onNext(finalResult("뭐 해?"));

        // 송신 실패는 곧 소켓 종료라 핸들러가 스스로 녹음을 마감한다.
        // 3000 = AI 단독, 4000 = 섞인 구간 — 둘 다 없어야 한다.
        verify(callRecordingService, timeout(2000)).save(eq(CALL_ID), any());
        assertThat(recordedSamples()).contains((short) 1000).doesNotContain((short) 3000, (short) 4000);
    }

    @Test
    void 소리가_없던_통화는_보관하지_않는다() {
        // 연결만 되고 아무도 말하지 않은 통화 — 빈 wav를 올릴 이유가 없다.
        WebSocketSession session = givenConnectedCall();

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        verifyNoInteractions(callRecordingService);
    }
}
