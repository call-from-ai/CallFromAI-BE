package com.example.umcCall.global.config;

import com.example.umcCall.domain.call.handler.CallAudioWebSocketHandler;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
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
    private final List<String> allowedOrigins;

    public WebSocketConfig(
            CallAudioWebSocketHandler callAudioWebSocketHandler,
            @Value("${cors.allowed-origins}") List<String> allowedOrigins
    ) {
        this.callAudioWebSocketHandler = callAudioWebSocketHandler;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // TODO(AI 연동): JWT 대신 짧은 수명 wsTicket 발급/검증 방식으로 변경 필요
        registry.addHandler(callAudioWebSocketHandler, "/ws/call")
                .setAllowedOrigins(allowedOrigins.toArray(String[]::new));
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
