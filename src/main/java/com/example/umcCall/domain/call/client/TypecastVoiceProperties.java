package com.example.umcCall.domain.call.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typecast(TTS) 접속 설정. application.yml의 {@code typecast.voice.*}에서 주입.
 * ({@code apiKey}는 환경변수 {@code TYPECAST_API_KEY})
 *
 * <p>⚠ <b>샘플레이트 설정이 없다</b> — 표준 TTS는 44.1kHz 고정이라 요청으로 못 바꾼다.
 * 우리가 정하는 값이 아니고, 다운스트림 계약도 이 숫자를 따른다.
 *
 * @param model     TTS 모델 버전({@code ssfm-v30} 등). 요청 필수값.
 * @param language  ISO 639-3({@code kor}). 생략하면 자동 감지라 "응." 같은 짧은 대사에서 오판한다.
 */
@ConfigurationProperties(prefix = "typecast.voice")
public record TypecastVoiceProperties(
        String baseUrl,
        String apiKey,
        String model,
        String language,
        String audioFormat,
        // 외부 API가 hang하면 합성 워커가 무한 대기하므로 상한을 둔다.
        int connectTimeoutMs,
        int readTimeoutMs
) {
}
