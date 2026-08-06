package com.example.umcCall.domain.call.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typecast(TTS) 접속 설정. application.yml의 {@code typecast.voice.*}에서 주입.
 * STT(CLOVA Speech)와 인증 체계가 다르다 — Speech는 gRPC Bearer secretKey, Typecast는 헤더 {@code X-API-KEY} 하나.
 * ({@code apiKey}는 환경변수 {@code TYPECAST_API_KEY})
 *
 * <p>⚠ <b>샘플레이트 설정이 없다.</b> Typecast 표준 TTS는 44.1kHz 고정이고 요청으로 조절할 수 없다
 * (CLOVA는 {@code sampling-rate}를 받았다). 그래서 이 값은 우리가 정하는 설정이 아니라
 * <b>외부가 정한 상수</b>이고, 다운스트림 계약도 그 숫자를 따른다.
 *
 * @param model     TTS 모델 버전({@code ssfm-v30} 등). 요청 필수값이라 기본값이 없다.
 * @param language  ISO 639-3 언어 코드({@code kor}). 생략하면 Typecast가 본문으로 자동 감지하는데,
 *                  통화는 한국어로 고정이라 명시해 짧은 대사("응.")에서 오판정되지 않게 한다.
 */
@ConfigurationProperties(prefix = "typecast.voice")
public record TypecastVoiceProperties(
        String baseUrl,
        String apiKey,
        String model,
        String language,
        String audioFormat,
        // 외부 API가 지연/hang하면 합성 워커가 무한 대기하므로 명시적 상한을 둔다.
        int connectTimeoutMs,
        int readTimeoutMs
) {
}
