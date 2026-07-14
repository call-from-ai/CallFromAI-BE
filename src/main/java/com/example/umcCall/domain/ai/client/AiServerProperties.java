package com.example.umcCall.domain.ai.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai.server")
public record AiServerProperties(
        String baseUrl,
        int connectTimeoutMs,
        int readTimeoutMs,
        String internalToken
) {
}
