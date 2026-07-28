package com.example.umcCall.domain.call.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.umcCall.domain.call.client.ClovaSpeechClient;
import com.example.umcCall.domain.call.client.ClovaSpeechProperties;
import com.example.umcCall.domain.call.client.ClovaVoiceClient;
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
        verify(session).close(CloseStatus.SERVER_ERROR);
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
    }

    @Test
    void 앱_종료_시_진행_중인_통화를_마감한다() throws Exception {
        WebSocketSession session = openSession();
        givenSttStreamOpens();
        lenient().when(chatHistoryProvider.recentHistory(any(Long.class), any(Integer.class)))
                .thenReturn(List.of());
        handler.afterConnectionEstablished(session);

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
