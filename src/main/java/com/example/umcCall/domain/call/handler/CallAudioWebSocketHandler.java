package com.example.umcCall.domain.call.handler;

import com.example.umcCall.domain.call.client.ClovaSpeechClient;
import com.google.protobuf.ByteString;
import com.nbp.cdncp.nest.grpc.proto.v1.NestConfig;
import com.nbp.cdncp.nest.grpc.proto.v1.NestData;
import com.nbp.cdncp.nest.grpc.proto.v1.NestRequest;
import com.nbp.cdncp.nest.grpc.proto.v1.NestResponse;
import com.nbp.cdncp.nest.grpc.proto.v1.RequestType;
import io.grpc.stub.StreamObserver;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

/**
 * 통화 오디오 업스트림 수신 → CLOVA STT 중계 핸들러. (CLAUDE.md 5장 2-3)
 *
 * <p>프론트가 WebSocket으로 올리는 raw PCM(16kHz / 모노 / 16-bit) 바이너리 프레임을
 * 세션별 CLOVA {@code recognize} gRPC 스트림으로 중계한다. WebSocket ↔ gRPC 변환기 역할.
 *
 * <ul>
 *   <li>연결 시: gRPC 스트림을 열고 {@code CONFIG}를 1회 전송.
 *   <li>바이너리 프레임 → {@code DATA}로 CLOVA에 중계.
 *   <li>텍스트(JSON) 프레임 → 제어 신호(통화 시작/끝 등). 이번 주는 로그만.
 *   <li>종료 시: gRPC 스트림을 {@code onCompleted()}로 닫는다.
 * </ul>
 *
 * <p>CLOVA 인식 결과는 이번 단계에선 <b>로그만</b> 남긴다. (partial/final 구분은 3단계,
 * 프론트로 내려보내기는 4단계.) 세션마다 스트림을 분리해 오디오가 섞이지 않게 한다. (CLAUDE.md 7장)
 * <p>이번 주 단순화: WebSocket 인증은 생략하고 "연결되면 받는다". (CLAUDE.md 5장)
 */
@Slf4j
@Component
public class CallAudioWebSocketHandler extends AbstractWebSocketHandler {

    /** CLOVA 인식 설정(JSON). 이번 주는 한국어 인식만. (튜닝 파라미터는 후순위) */
    private static final String CONFIG_JSON = "{\"transcription\":{\"language\":\"ko\"}}";

    private final ClovaSpeechClient clovaSpeechClient;

    /** 세션 ID → 해당 세션의 CLOVA 업스트림 옵저버(요청을 밀어넣는 통로). */
    private final ConcurrentHashMap<String, StreamObserver<NestRequest>> sessionStreams =
            new ConcurrentHashMap<>();

    public CallAudioWebSocketHandler(ClovaSpeechClient clovaSpeechClient) {
        this.clovaSpeechClient = clovaSpeechClient;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String sessionId = session.getId();
        StreamObserver<NestRequest> requestObserver =
                clovaSpeechClient.openRecognizeStream(new ClovaResponseObserver(sessionId));

        // CONFIG 1회 → 이후 오디오는 DATA로. (CLOVA recognize 스트림 규약)
        requestObserver.onNext(NestRequest.newBuilder()
                .setType(RequestType.CONFIG)
                .setConfig(NestConfig.newBuilder().setConfig(CONFIG_JSON).build())
                .build());

        sessionStreams.put(sessionId, requestObserver);
        log.info("[Call] WebSocket 연결 · CLOVA 스트림 개설. session={}", sessionId);
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        StreamObserver<NestRequest> requestObserver = sessionStreams.get(session.getId());
        if (requestObserver == null) {
            log.warn("[Call] CLOVA 스트림이 없어 오디오를 버림. session={}", session.getId());
            return;
        }
        ByteBuffer payload = message.getPayload();
        byte[] chunk = new byte[payload.remaining()];
        payload.get(chunk);

        requestObserver.onNext(NestRequest.newBuilder()
                .setType(RequestType.DATA)
                .setData(NestData.newBuilder().setChunk(ByteString.copyFrom(chunk)).build())
                .build());
        log.debug("[Call] 오디오 {} bytes 중계. session={}", chunk.length, session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 제어 신호(통화 시작/끝 등)는 JSON 텍스트로 온다. 이번 주는 로그만. (파싱/상태머신은 후순위)
        log.info("[Call] 제어 메시지 수신. session={}, payload={}", session.getId(), message.getPayload());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        completeStream(session.getId());
        log.info("[Call] WebSocket 종료. session={}, status={}", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("[Call] 전송 오류. session={}", session.getId(), exception);
        completeStream(session.getId());
    }

    /** 세션의 CLOVA 업스트림을 정상 종료한다. */
    private void completeStream(String sessionId) {
        StreamObserver<NestRequest> requestObserver = sessionStreams.remove(sessionId);
        if (requestObserver == null) {
            return;
        }
        try {
            requestObserver.onCompleted();
        } catch (RuntimeException e) {
            log.warn("[Call] CLOVA 스트림 종료 실패. session={}", sessionId, e);
        }
    }

    /** CLOVA 인식 결과 콜백. 이번 단계는 로그만. (프론트 전달은 4단계) */
    private record ClovaResponseObserver(String sessionId) implements StreamObserver<NestResponse> {

        @Override
        public void onNext(NestResponse response) {
            log.info("[Clova] 인식 결과. session={}, contents={}", sessionId, response.getContents());
        }

        @Override
        public void onError(Throwable t) {
            log.error("[Clova] 스트림 오류. session={}", sessionId, t);
        }

        @Override
        public void onCompleted() {
            log.info("[Clova] 스트림 완료. session={}", sessionId);
        }
    }
}
