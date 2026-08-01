package com.example.umcCall.domain.call.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.umcCall.domain.call.client.ClovaSpeechClient;
import com.example.umcCall.domain.call.client.ClovaSpeechProperties;
import com.example.umcCall.domain.call.client.ClovaVoiceClient;
import com.example.umcCall.domain.call.enums.CallEndReason;
import com.example.umcCall.domain.call.event.CallEndedEvent;
import com.example.umcCall.domain.call.port.ChatHistoryProvider;
import com.example.umcCall.domain.call.service.CallConversationService;
import com.example.umcCall.domain.call.service.CallHistoryService;
import com.example.umcCall.domain.call.service.CallService;
import com.example.umcCall.domain.call.ticket.WsTicket;
import com.example.umcCall.domain.call.ticket.WsTicketHandshakeInterceptor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.stub.StreamObserver;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    @Mock private ChatHistoryProvider chatHistoryProvider;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private CallAudioWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        handler = new CallAudioWebSocketHandler(
                clovaSpeechClient, clovaVoiceClient, callConversationService, callService,
                callHistoryService, chatHistoryProvider,
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
        lenient().when(chatHistoryProvider.recentHistory(any(Long.class), any(Integer.class)))
                .thenReturn(List.of());
        handler.afterConnectionEstablished(session);
        return session;
    }

    @Test
    void 연결이_준비되면_CALL_READY를_보낸다() throws Exception {
        WebSocketSession session = openSession();
        givenSttStreamOpens();
        lenient().when(chatHistoryProvider.recentHistory(any(Long.class), any(Integer.class)))
                .thenReturn(List.of());

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
}
