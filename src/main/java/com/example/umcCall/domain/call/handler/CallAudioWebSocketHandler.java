package com.example.umcCall.domain.call.handler;

import com.example.umcCall.domain.call.client.ClovaSpeechClient;
import com.example.umcCall.domain.call.client.ClovaVoiceClient;
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
 * 통화 오디오 양방향 채널 핸들러. 세션(통화)마다 분리해 처리한다.
 * <ul>
 *   <li><b>업스트림</b>: 바이너리 프레임(raw PCM 16kHz/모노/16-bit)을 세션별 CLOVA STT gRPC 스트림으로 중계.
 *   <li><b>다운스트림</b>: STT final 전사를 TTS로 합성해 wav를 그대로 바이너리 프레임으로 송신.
 *   <li>제어 신호는 텍스트(JSON) 프레임. (파싱/상태머신은 후순위 — 지금은 로그만)
 * </ul>
 * 다운스트림 텍스트 소스는 지금 <b>에코</b>(내 말이 그대로 돌아옴)다 — 배관을 잇기 위한 임시 형태이고,
 * 이 자리에 나중에 LLM이 대체 투입된다. ({@link #submitEcho})
 */
@Slf4j
@Component
public class CallAudioWebSocketHandler extends AbstractWebSocketHandler {

    /** CLOVA 인식 설정. 한국어. (EPD 등 튜닝은 후순위) */
    private static final String CONFIG_JSON = "{\"transcription\":{\"language\":\"ko\"}}";

    /** 에코 화자. 캐릭터 개념이 없는 동안의 임시값 — 캐릭터 음성이 들어올 자리다. */
    private static final String ECHO_SPEAKER = "nara";

    private final ClovaSpeechClient clovaSpeechClient;
    private final ClovaVoiceClient clovaVoiceClient;
    private final ObjectMapper objectMapper;

    /**
     * WebSocket 세션 ID → 그 세션으로 진행 중인 통화.
     * WS 수신 · gRPC 콜백 · 워커 세 스레드가 만나는 지점이라 동시성 맵이다.
     */
    private final ConcurrentHashMap<String, ActiveCall> activeCalls = new ConcurrentHashMap<>();

    public CallAudioWebSocketHandler(ClovaSpeechClient clovaSpeechClient,
                                     ClovaVoiceClient clovaVoiceClient,
                                     ObjectMapper objectMapper) {
        this.clovaSpeechClient = clovaSpeechClient;
        this.clovaVoiceClient = clovaVoiceClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String sessionId = session.getId();
        try {
            StreamObserver<NestRequest> requestObserver =
                    clovaSpeechClient.openRecognizeStream(new ClovaResponseObserver(session));
            SessionStream stream = new SessionStream(requestObserver);
            // 스트림 개설에 성공한 뒤 워커를 만든다 — 실패했다면 정리할 워커도 없다.
            // CONFIG 전에 등록해두므로, onNext가 처음 뜰 땐 이미 통화가 맵에 있다.
            activeCalls.put(sessionId, new ActiveCall(stream, Executors.newSingleThreadExecutor()));

            // CONFIG 1회 → 이후 오디오는 DATA로. (CLOVA recognize 스트림 규약)
            stream.send(NestRequest.newBuilder()
                    .setType(RequestType.CONFIG)
                    .setConfig(NestConfig.newBuilder().setConfig(CONFIG_JSON).build())
                    .build());

            log.info("[Call] WebSocket 연결 · CLOVA 스트림 개설. session={}", sessionId);
        } catch (RuntimeException e) {
            // CLOVA 스트림 개설 자체가 실패한 경우. 스트림 없는 통화를 남기지 않도록 즉시 닫는다.
            // (비동기 연결 실패는 ClovaResponseObserver.onError로 따로 처리됨)
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
        SessionStream stream = call.stream();
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
            terminateCall(session, CloseStatus.SERVER_ERROR);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 제어 신호(통화 시작/끝). 파싱/상태머신은 후순위 — 지금은 로그만.
        log.info("[Call] 제어 메시지 수신. session={}, payload={}", session.getId(), message.getPayload());
    }

    /**
     * final 전사를 TTS로 합성해 다운스트림으로 돌려보낸다. (에코 — LLM 자리)
     * 호출자(gRPC 콜백)를 잡아두지 않도록 통화 워커에 넘기고 즉시 반환한다.
     * ★ 나중에 LLM 호출·전사 DB 저장이 들어올 자리가 이 워커 안이다.
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
     * 합성된 wav를 이 세션 소켓에만 바이너리 프레임으로 내려보낸다. (다운스트림 송신)
     * CLOVA Voice가 준 wav를 헤더째 그대로 보낸다 — 서버는 변환하지 않고, 프론트가 헤더로 스펙을 읽는다.
     * 문장 단위 wav 하나가 곧 재생 가능한 완결 단위이므로 프레임 하나에 그대로 싣는다.
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

    /**
     * 통화를 정상 종료한다: CLOVA 업스트림 half-close({@code complete}) + 워커 정리.
     * <b>소켓이 이미 닫힌 뒤</b>의 뒷정리라 소켓은 건드리지 않는다. (짝: {@link #terminateCall})
     */
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
     * <b>서버가 먼저</b> 통화를 끝낸다: 스트림 폐기({@code terminate}) + 워커 정리 + WebSocket 닫기.
     * {@link #completeCall}과 달리 소켓이 아직 살아 있어 우리가 닫아야 하고,
     * CLOVA에 half-close를 보내지 않고 그냥 버린다. (CLOVA 에러/완료 · 개설 실패 · 중계/송신 실패 공용)
     * {@code status}가 곧 종료 사유다 — 원인/로그는 호출부에서 남긴다.
     * 재개설(투명 복원)은 지금 범위 밖 — 닫아서 클라이언트가 재연결하도록 둔다.
     * 맵에서 먼저 제거하므로, close가 부르는 afterConnectionClosed → completeCall은 no-op이 된다.
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
     * 진행 중인 통화 하나가 들고 있는 것 전부. 통화 시작에 같이 생기고 종료에 같이 죽는다.
     * 물건마다 맵을 나누면 손으로 동기화하게 되고 한쪽만 정리하는 실수가 나므로(워커를 빠뜨리면
     * 스레드가 샌다) 하나로 묶어 {@code remove} 한 번에 함께 딸려 나오게 한다.
     * 통화 스코프 상태(전사 버퍼·LLM 컨텍스트 등)가 늘면 여기에 필드로 붙인다.
     *
     * <p>전송 수단인 {@link WebSocketSession}과 다른 개념이라 이름을 구분한다 — 이쪽은 도메인(통화)이다.
     * DB에 남는 통화 기록({@code domain.call.entity.Call})과도 구분된다: 이건 메모리에만 있는 진행 중인 통화다.
     *
     * @param worker TTS 워커. 통화당 단일 스레드라 제출 순서 = 실행 순서(에코 순서 보장).
     *               gRPC 콜백 스레드는 전 통화 공유라, 거기서 합성하면 다른 통화까지 밀린다.
     */
    private record ActiveCall(SessionStream stream, ExecutorService worker) {
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

                // partial은 버린다 — 계속 바뀌는 중간 결과라 같은 말이 여러 번 나간다.
                // ⚠ 이 메서드는 즉시 반환해야 한다. 합성/송신은 submitEcho가 워커로 넘긴다.
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
