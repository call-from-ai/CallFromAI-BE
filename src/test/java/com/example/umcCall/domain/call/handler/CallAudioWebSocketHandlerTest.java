package com.example.umcCall.domain.call.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

/**
 * 오디오 업스트림 수신 핸들러가 바이너리 프레임을 세션별 .pcm 파일로 저장하는지 검증한다.
 * (CLAUDE.md 5장 1단계: STT 붙이기 전, 업로드/저장이 되는지부터 확인)
 */
class CallAudioWebSocketHandlerTest {

    @Test
    void 바이너리_프레임을_세션별_pcm_파일로_저장한다(@TempDir Path tempDir) throws Exception {
        CallAudioWebSocketHandler handler = new CallAudioWebSocketHandler(tempDir.toString());
        WebSocketSession session = mockSession("session-1");

        byte[] frame1 = "PCM_CHUNK_1".getBytes(StandardCharsets.UTF_8);
        byte[] frame2 = "PCM_CHUNK_2".getBytes(StandardCharsets.UTF_8);

        handler.afterConnectionEstablished(session);
        handler.handleBinaryMessage(session, new BinaryMessage(frame1));
        handler.handleBinaryMessage(session, new BinaryMessage(frame2));
        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        Path pcm = onlyPcmFile(tempDir);
        assertThat(pcm.getFileName().toString()).contains("session-1").endsWith(".pcm");
        // 두 프레임이 순서대로 이어 붙어 저장되어야 한다.
        byte[] saved = Files.readAllBytes(pcm);
        assertThat(new String(saved, StandardCharsets.UTF_8)).isEqualTo("PCM_CHUNK_1PCM_CHUNK_2");
    }

    @Test
    void 세션마다_오디오가_섞이지_않도록_파일을_분리한다(@TempDir Path tempDir) throws Exception {
        CallAudioWebSocketHandler handler = new CallAudioWebSocketHandler(tempDir.toString());
        WebSocketSession a = mockSession("A");
        WebSocketSession b = mockSession("B");

        handler.afterConnectionEstablished(a);
        handler.afterConnectionEstablished(b);
        handler.handleBinaryMessage(a, new BinaryMessage("AAAA".getBytes(StandardCharsets.UTF_8)));
        handler.handleBinaryMessage(b, new BinaryMessage("BBBB".getBytes(StandardCharsets.UTF_8)));
        handler.afterConnectionClosed(a, CloseStatus.NORMAL);
        handler.afterConnectionClosed(b, CloseStatus.NORMAL);

        try (Stream<Path> files = Files.list(tempDir)) {
            List<Path> pcms = files.filter(p -> p.toString().endsWith(".pcm")).toList();
            assertThat(pcms).hasSize(2);
            for (Path p : pcms) {
                String content = Files.readString(p);
                String name = p.getFileName().toString();
                if (name.contains("-A.pcm")) {
                    assertThat(content).isEqualTo("AAAA");
                } else if (name.contains("-B.pcm")) {
                    assertThat(content).isEqualTo("BBBB");
                }
            }
        }
    }

    private WebSocketSession mockSession(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        return session;
    }

    private Path onlyPcmFile(Path dir) throws Exception {
        try (Stream<Path> files = Files.list(dir)) {
            List<Path> pcms = files.filter(p -> p.toString().endsWith(".pcm")).toList();
            assertThat(pcms).hasSize(1);
            return pcms.get(0);
        }
    }
}
