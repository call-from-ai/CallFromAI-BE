package com.example.umcCall.domain.call.client;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * CLOVA Speech(STT) gRPC 접속 설정. application.yml의 {@code clova.speech.*}에서 주입.
 * (secretKey는 환경변수 CLOVA_SPEECH_SECRET)
 * <p>TTS는 인증 체계가 달라 별도다 — {@link ClovaVoiceProperties} 참고.
 *
 * @param gapThresholdMs 턴 끝 감지용 침묵 임계(ms). semanticEpd.gapThreshold로 CONFIG에 실려,
 *                       이만큼 침묵하면 final(epdType=gap)이 확정된다. 지연 vs 조기컷의 다이얼.
 *                       0/누락·자릿수 오타면 턴 감지가 조용히 망가지므로 시작 시 하한(300ms)을 검증한다.
 */
@Validated
@ConfigurationProperties(prefix = "clova.speech")
public record ClovaSpeechProperties(
        String host,
        int port,
        String secretKey,
        @Min(value = 300,
                message = "clova.speech.gap-threshold-ms는 최소 300ms 이상이어야 합니다")
        int gapThresholdMs
) {}
