package com.example.umcCall.domain.call.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
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
import com.example.umcCall.domain.call.service.CallConversationService;
import com.example.umcCall.domain.call.service.CallHistoryService;
import com.example.umcCall.domain.call.service.CallService;
import com.example.umcCall.domain.call.ticket.WsTicket;
import com.example.umcCall.domain.call.ticket.WsTicketHandshakeInterceptor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nbp.cdncp.nest.grpc.proto.v1.NestResponse;
import io.grpc.stub.StreamObserver;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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

    @Mock private ClovaSpeechClient clovaSpeechClient;
    @Mock private ClovaVoiceClient clovaVoiceClient;
    @Mock private CallConversationService callConversationService;
    @Mock private CallService callService;
    @Mock private CallHistoryService callHistoryService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private CallAudioWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        handler = new CallAudioWebSocketHandler(
                clovaSpeechClient, clovaVoiceClient, callConversationService, callService,
                callHistoryService,
                new ClovaSpeechProperties("clovaspeech-gw.ncloud.com", 50051, "secret", 700),
                objectMapper);
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
        // ⚠ 이모지는 소리로 안 나갔으니 이력·전사에도 안 남는다. 통화 전문은 <b>실제로 들린 것</b>의
        // 기록이다 — #122 리뷰에서 "원문 보존" 안을 검토했다가 이 이유로 기각했다(2026-08-03).
        verify(callHistoryService, timeout(2000))
                .appendHistory(CALL_ID, CallSpeaker.AI, "응, 오늘은 그냥 집에 있었어.");
    }

    @Test
    void 이모지만_있는_대사는_assistant를_아예_안_남긴다() throws Exception {
        // 위 규칙의 경계. 소리가 한 번도 안 나갔으면 assistant 자체를 안 남긴다 —
        // 이모지 하나가 "AI가 말했다"로 둔갑하면 다음 턴이 사용자 모르는 맥락 위에서 나온다.
        WebSocketSession session = givenConnectedCall();
        StreamObserver<NestResponse> stt = captureSttObserver();
        givenAiStreams("🙂");

        stt.onNext(finalResult("뭐 해?"));

        verify(session, after(500).never()).sendMessage(any(BinaryMessage.class));
        verify(callHistoryService, never()).appendHistory(eq(CALL_ID), eq(CallSpeaker.AI), any());
    }

    @Test
    void 끼어들면_문장이_완성되기_전에도_스트림_소비를_멈춘다() throws Exception {
        // ⚠ 취소 확인이 speak()에만 있으면 <b>문장이 완성돼야</b> 알아챈다 — 종결 부호 없이 흐르는
        // 응답에선 MAX_CHARS(120자)까지 워커가 SSE를 계속 읽고, 그동안 사용자가 방금 끼어들며 만든
        // 다음 턴이 밀린다. 조각 도착마다 확인해야 소비가 즉시 멈춘다(PR #122 리뷰).
        WebSocketSession session = givenConnectedCall();
        StreamObserver<NestResponse> stt = captureSttObserver();
        AtomicInteger consumed = new AtomicInteger();
        doAnswer(invocation -> {
            Consumer<String> onChunk = invocation.getArgument(3);
            stt.onNext(partialResult("아 맞다")); // 첫 조각이 오기도 전에 끼어들었다
            try {
                for (int i = 0; i < 5; i++) {
                    onChunk.accept("종결 부호 없이 계속 이어지는 말 "); // 문장이 완성되지 않는다
                    consumed.incrementAndGet();
                }
            } catch (RuntimeException expected) {
                // 턴이 접히면 여기로 온다 = 스트림 소비가 멈췄다는 뜻
            }
            return null;
        }).when(callConversationService).respondStream(any(), any(), any(), any());

        stt.onNext(finalResult("뭐 해?"));

        verify(clovaVoiceClient, after(500).never()).synthesize(any(), any());
        // 확인이 speak()에만 있었다면 5조각을 전부 읽고 나서야 멈춘다.
        assertThat(consumed.get()).isZero();
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
}
