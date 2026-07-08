package com.example.umcCall.global.config;

import com.example.umcCall.domain.call.handler.CallAudioWebSocketHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/**
 * 통화 오디오 WebSocket 채널 등록. (CLAUDE.md 3장 채널 구조)
 *
 * <p>업스트림/다운스트림 모두 이 하나의 WebSocket 연결로 통일한다.
 * 오디오는 바이너리 프레임, 제어 신호는 텍스트(JSON) 프레임으로 주고받는다.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final CallAudioWebSocketHandler callAudioWebSocketHandler;

    public WebSocketConfig(CallAudioWebSocketHandler callAudioWebSocketHandler) {
        this.callAudioWebSocketHandler = callAudioWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 이번 주 단순화: 인증 생략, origin 제한 없이 "연결되면 받는다". (인증/origin 제한은 후순위)
        registry.addHandler(callAudioWebSocketHandler, "/ws/call")
                .setAllowedOriginPatterns("*");
    }

    /**
     * 오디오 바이너리 프레임이 기본 버퍼(8KB)보다 클 수 있으므로 상향 조정한다.
     * (raw PCM 청크 크기가 프론트 청킹 주기에 따라 커질 수 있음)
     */
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxBinaryMessageBufferSize(1024 * 1024); // 1MB
        container.setMaxTextMessageBufferSize(64 * 1024);     // 64KB
        return container;
    }
}
