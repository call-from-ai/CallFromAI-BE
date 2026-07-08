package com.example.umcCall.domain.call.handler;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

/**
 * 통화 오디오 업스트림 수신 핸들러. (CLAUDE.md 5장 1단계)
 *
 * <p>프론트가 WebSocket으로 올리는 raw PCM(16kHz / 모노 / 16-bit) 바이너리 프레임을
 * 세션별 {@code .pcm} 파일로 저장한다. STT 연동 전, "업로드 자체가 되는지"를 검증하는 단계다.
 *
 * <ul>
 *   <li>바이너리 프레임 → 오디오 데이터. 세션 파일에 append.
 *   <li>텍스트(JSON) 프레임 → 제어 신호(통화 시작/끝 등). 이번 주는 로그만 남긴다.
 * </ul>
 *
 * <p>세션마다 오디오가 섞이지 않도록 파일/스트림을 세션별로 분리한다. (CLAUDE.md 7장)
 * <p>이번 주 단순화: WebSocket 인증은 생략하고 "연결되면 받는다". (CLAUDE.md 5장)
 */
@Slf4j
@Component
public class CallAudioWebSocketHandler extends AbstractWebSocketHandler {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final Path recordingDir;

    /** 세션 ID → 해당 세션의 오디오 출력 스트림. */
    private final ConcurrentHashMap<String, OutputStream> sessionStreams = new ConcurrentHashMap<>();

    public CallAudioWebSocketHandler(
            @Value("${call.audio.recording-dir:./recordings}") String recordingDir) {
        this.recordingDir = Path.of(recordingDir);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Files.createDirectories(recordingDir);
        Path file = recordingDir.resolve(
                LocalDateTime.now().format(TS) + "-" + session.getId() + ".pcm");
        OutputStream out = new BufferedOutputStream(Files.newOutputStream(file));
        sessionStreams.put(session.getId(), out);
        log.info("[Call] WebSocket 연결. session={}, file={}", session.getId(), file.toAbsolutePath());
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        OutputStream out = sessionStreams.get(session.getId());
        if (out == null) {
            log.warn("[Call] 출력 스트림이 없어 오디오를 버림. session={}", session.getId());
            return;
        }
        ByteBuffer payload = message.getPayload();
        byte[] chunk = new byte[payload.remaining()];
        payload.get(chunk);
        out.write(chunk);
        log.debug("[Call] 오디오 {} bytes 수신. session={}", chunk.length, session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 제어 신호(통화 시작/끝 등)는 JSON 텍스트로 온다. 이번 주는 로그만. (파싱/상태머신은 후순위)
        log.info("[Call] 제어 메시지 수신. session={}, payload={}", session.getId(), message.getPayload());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        closeStream(session.getId());
        log.info("[Call] WebSocket 종료. session={}, status={}", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("[Call] 전송 오류. session={}", session.getId(), exception);
        closeStream(session.getId());
    }

    private void closeStream(String sessionId) {
        OutputStream out = sessionStreams.remove(sessionId);
        if (out == null) {
            return;
        }
        try {
            out.close();
        } catch (IOException e) {
            log.warn("[Call] 출력 스트림 종료 실패. session={}", sessionId, e);
        }
    }
}
