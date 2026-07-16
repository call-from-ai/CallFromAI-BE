package com.example.umcCall.domain.call.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CLOVA Speech(STT) gRPC 접속 설정. application.yml의 {@code clova.speech.*}에서 주입.
 * (secretKey는 환경변수 CLOVA_SPEECH_SECRET)
 * <p>TTS는 인증 체계가 달라 별도다 — {@link ClovaVoiceProperties} 참고.
 *
 * @param gapThresholdMs 턴 끝 감지용 침묵 임계(ms). semanticEpd.gapThreshold로 CONFIG에 실려,
 *                       이만큼 침묵하면 final(epdType=gap)이 확정된다. 지연 vs 조기컷의 다이얼.
 */
@ConfigurationProperties(prefix = "clova.speech")
public record ClovaSpeechProperties(String host, int port, String secretKey, int gapThresholdMs) {
}
