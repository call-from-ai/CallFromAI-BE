package com.example.umcCall.domain.call.handler;

import com.example.umcCall.domain.call.client.ClovaSpeechClient;
import com.example.umcCall.domain.call.dto.NestRecognizeResult;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * 통화 오디오 업스트림(WebSocket) → CLOVA STT(gRPC) 중계 핸들러.
 * 바이너리 프레임(raw PCM 16kHz/모노/16-bit)을 세션별 gRPC 스트림으로 중계하고,
 * 인식 결과는 partial/final을 구분해 로그로 남긴다. (세션마다 스트림 분리)
 */
@Slf4j
@Component
public class CallAudioWebSocketHandler extends AbstractWebSocketHandler {

    /** CLOVA 인식 설정. 한국어. (EPD 등 튜닝은 후순위) */
    private static final String CONFIG_JSON = "{\"transcription\":{\"language\":\"ko\"}}";

    private final ClovaSpeechClient clovaSpeechClient;
    private final ObjectMapper objectMapper;

    /** 세션 ID → 해당 세션의 CLOVA 업스트림 옵저버(요청을 밀어넣는 통로). */
    private final ConcurrentHashMap<String, StreamObserver<NestRequest>> sessionStreams =
            new ConcurrentHashMap<>();

    public CallAudioWebSocketHandler(ClovaSpeechClient clovaSpeechClient, ObjectMapper objectMapper) {
        this.clovaSpeechClient = clovaSpeechClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String sessionId = session.getId();
        StreamObserver<NestRequest> requestObserver =
                clovaSpeechClient.openRecognizeStream(new ClovaResponseObserver(sessionId, objectMapper));

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
        // 제어 신호(통화 시작/끝). 파싱/상태머신은 후순위 — 지금은 로그만.
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

    /** CLOVA 인식 결과 콜백. partial/final을 구분해 로그. */
    private record ClovaResponseObserver(String sessionId, ObjectMapper objectMapper)
            implements StreamObserver<NestResponse> {

        @Override
        public void onNext(NestResponse response) {
            String contents = response.getContents();
            try {
                NestRecognizeResult result = objectMapper.readValue(contents, NestRecognizeResult.class);
                if (!result.isTranscription()) { // config 응답 등
                    log.debug("[Clova] 비전사 응답. session={}, contents={}", sessionId, contents);
                    return;
                }
                String tag = result.isFinal() ? "final" : "partial";
                log.info("[Clova] [{}] session={}, text={}", tag, sessionId, result.text());
            } catch (Exception e) {
                log.warn("[Clova] 응답 파싱 실패. session={}, contents={}", sessionId, contents, e); // 원문 폴백
            }
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
