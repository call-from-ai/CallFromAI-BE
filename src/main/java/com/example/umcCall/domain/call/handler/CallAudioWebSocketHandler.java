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
import java.io.IOException;
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

    /** 세션 ID → 해당 세션의 CLOVA 업스트림(전송/종료를 직렬화하는 래퍼). */
    private final ConcurrentHashMap<String, SessionStream> sessionStreams =
            new ConcurrentHashMap<>();

    public CallAudioWebSocketHandler(ClovaSpeechClient clovaSpeechClient, ObjectMapper objectMapper) {
        this.clovaSpeechClient = clovaSpeechClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String sessionId = session.getId();
        try {
            StreamObserver<NestRequest> requestObserver =
                    clovaSpeechClient.openRecognizeStream(new ClovaResponseObserver(session));
            SessionStream stream = new SessionStream(requestObserver);
            sessionStreams.put(sessionId, stream);

            // CONFIG 1회 → 이후 오디오는 DATA로. (CLOVA recognize 스트림 규약)
            stream.send(NestRequest.newBuilder()
                    .setType(RequestType.CONFIG)
                    .setConfig(NestConfig.newBuilder().setConfig(CONFIG_JSON).build())
                    .build());

            log.info("[Call] WebSocket 연결 · CLOVA 스트림 개설. session={}", sessionId);
        } catch (RuntimeException e) {
            // CLOVA 스트림 개설 자체가 실패한 경우. 스트림 없는 세션을 남기지 않도록 즉시 닫는다.
            // (비동기 연결 실패는 ClovaResponseObserver.onError로 따로 처리됨)
            log.error("[Call] CLOVA 스트림 개설 실패 → WebSocket 종료. session={}", sessionId, e);
            endSession(session, CloseStatus.SERVER_ERROR);
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        SessionStream stream = sessionStreams.get(session.getId());
        if (stream == null) {
            log.warn("[Call] CLOVA 스트림이 없어 오디오를 버림. session={}", session.getId());
            return;
        }
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
            // 종료 플래그로 대부분 걸러지지만, 그 밖의 전송 실패는 여기서 세션을 정리·종료한다.
            log.error("[Call] 오디오 중계 실패 → WebSocket 종료. session={}", session.getId(), e);
            endSession(session, CloseStatus.SERVER_ERROR);
        }
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

    /** 세션의 CLOVA 업스트림을 정상 종료(half-close)한다. WebSocket 정상 종료 시 호출. */
    private void completeStream(String sessionId) {
        SessionStream stream = sessionStreams.remove(sessionId);
        if (stream == null) {
            return;
        }
        try {
            stream.complete();
        } catch (RuntimeException e) {
            log.warn("[Call] CLOVA 스트림 종료 실패. session={}", sessionId, e);
        }
    }

    /**
     * 세션 종료 공용: 스트림을 맵에서 제거해 이후 전송을 막고(terminate) WebSocket도 닫는다.
     * (CLOVA 에러/완료 · 초기 개설 실패 · 오디오 중계 실패 공용. 원인/로그는 호출부에서.)
     * 재개설(투명 복원)은 지금 범위 밖 — 닫아서 클라이언트가 재연결하도록 둔다.
     * 맵에서 먼저 제거하므로, close가 부르는 afterConnectionClosed → completeStream은 no-op이 된다.
     */
    private void endSession(WebSocketSession session, CloseStatus status) {
        SessionStream stream = sessionStreams.remove(session.getId());
        if (stream != null) {
            stream.terminate(); // 이후 send()는 무시됨
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
     * 세션 하나의 CLOVA 업스트림 래퍼. gRPC {@link StreamObserver}는 스레드 안전이 아니므로,
     * {@code send}(onNext)·{@code complete}(onCompleted)를 직렬화하고 종료 후 전송을 막는다.
     * 락은 세션별이라 다른 통화를 막지 않는다.
     */
    private static final class SessionStream {

        private final StreamObserver<NestRequest> requestObserver;
        private boolean terminated = false;

        private SessionStream(StreamObserver<NestRequest> requestObserver) {
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
            } catch (Exception e) {
                log.warn("[Clova] 응답 파싱 실패. session={}, contents={}", sessionId, contents, e); // 원문 폴백
            }
        }

        @Override
        public void onError(Throwable t) {
            log.error("[Clova] 스트림 오류 → WebSocket 종료. session={}", session.getId(), t);
            endSession(session, CloseStatus.SERVER_ERROR);
        }

        @Override
        public void onCompleted() {
            // CLOVA가 스트림을 끝낸 경우(정상/선종료). 남은 맵 정리 + WebSocket 정리.
            log.info("[Clova] 스트림 완료 → WebSocket 정리. session={}", session.getId());
            endSession(session, CloseStatus.NORMAL);
        }
    }
}
