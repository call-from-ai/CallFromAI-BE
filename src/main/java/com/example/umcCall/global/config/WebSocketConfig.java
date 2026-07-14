package com.example.umcCall.global.config;

import com.example.umcCall.domain.call.handler.CallAudioWebSocketHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/**
 * 통화 오디오 WebSocket 채널 등록.
 * 오디오는 바이너리 프레임, 제어 신호는 텍스트(JSON) 프레임.
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
        // 인증 생략 + origin 무제한 (임시). ⚠ main(자동배포) 전 인증·origin 제한 필수.
        registry.addHandler(callAudioWebSocketHandler, "/ws/call")
                .setAllowedOriginPatterns("*");
    }

    /** 오디오 바이너리 프레임이 기본 버퍼(8KB)보다 커질 수 있어 상향 조정. */
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxBinaryMessageBufferSize(1024 * 1024);
        container.setMaxTextMessageBufferSize(64 * 1024);
        return container;
    }
}
