package com.example.umcCall.domain.ai.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 서버 연동 설정.
 *
 * <p>⚠ read timeout이 <b>둘</b>인 이유: 단발 {@code /chat}은 "요청 후 응답 전체가 올 때까지"를 재는 반면,
 * 스트리밍 {@code /chat/stream}은 <b>조각과 조각 사이</b>를 잰다(SSE 라인 단위 블로킹 read).
 * 성격이 다른 두 값을 한 설정으로 묶으면 한쪽에 맞춘 값이 다른 쪽에서 엉뚱하게 동작한다.
 */
@ConfigurationProperties(prefix = "ai.server")
public record AiServerProperties(
        String baseUrl,
        int connectTimeoutMs,
        int readTimeoutMs,
        Integer streamReadTimeoutMs,
        String internalToken
) {

    /**
     * 스트리밍 read timeout 기본값(ms). <b>조각 사이 침묵</b>의 상한이지 응답 전체 시간이 아니다.
     *
     * <p>근거: 첫 토큰까지가 가장 긴 구간인데 AI 서버가 Gemini thinking을 켜도 실측 ~7.5초였다.
     * 거기에 여유를 둔 값이라, 이보다 오래 조용하면 정상 스트림이 아니라 half-open으로 본다.
     *
     * <p>⚠ 단발 {@code readTimeoutMs}(운영 60초)를 스트리밍에 그대로 쓰면 안 된다 — 죽은 연결 하나가
     * 그 통화의 워커를 60초간 붙잡아 통화가 그동안 벙어리가 된다.
     */
    private static final int DEFAULT_STREAM_READ_TIMEOUT_MS = 15000;

    public AiServerProperties {
        // 설정 누락 시 0(=무제한)으로 떨어지면 죽은 스트림을 영영 못 끊는다. 기본값으로 막는다.
        if (streamReadTimeoutMs == null || streamReadTimeoutMs <= 0) {
            streamReadTimeoutMs = DEFAULT_STREAM_READ_TIMEOUT_MS;
        }
    }
}
