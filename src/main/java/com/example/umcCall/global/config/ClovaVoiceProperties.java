package com.example.umcCall.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CLOVA Voice(TTS) 접속 설정. application.yml의 {@code clova.voice.*}에서 주입.
 * STT와 인증 체계가 다르다 — Speech는 gRPC Bearer secretKey, Voice는 NCP API Gateway 키 2개.
 * (clientId/clientSecret은 환경변수 CLOVA_VOICE_CLIENT_ID / CLOVA_VOICE_CLIENT_SECRET)
 */
@ConfigurationProperties(prefix = "clova.voice")
public record ClovaVoiceProperties(
        String baseUrl,
        String clientId,
        String clientSecret,
        String format,
        int samplingRate,
        // 외부 API가 지연/hang하면 합성 워커가 무한 대기하므로 명시적 상한을 둔다.
        int connectTimeoutMs,
        int readTimeoutMs
) {
}
