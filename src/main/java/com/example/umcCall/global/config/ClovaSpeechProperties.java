package com.example.umcCall.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CLOVA Speech(STT) gRPC 접속 설정. application.yml의 {@code clova.speech.*}에서 주입.
 * (secretKey는 환경변수 CLOVA_SPEECH_SECRET)
 * <p>TTS는 인증 체계가 달라 별도다 — {@link ClovaVoiceProperties} 참고.
 */
@ConfigurationProperties(prefix = "clova.speech")
public record ClovaSpeechProperties(String host, int port, String secretKey) {
}
