package com.example.umcCall.domain.call.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * CLOVA {@code recognize} 응답({@code NestResponse.contents}) JSON을 파싱한 결과. (CLAUDE.md 5장 3단계)
 *
 * <p>필요한 필드만 담고 나머지(alignInfos, confidence, timestamp 등)는 무시한다.
 * partial(중간)/final(확정) 구분은 EPD 신호인 {@code transcription.epdType}로 판정한다:
 * EPD가 구간을 끊었으면(=값이 채워지면) final, 아직 흐르는 중이면(=빈 값) partial.
 * (EPD를 언제 끊을지는 CONFIG에서 조절 — 이 판정 코드는 그와 무관하게 동작.)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NestRecognizeResult(List<String> responseType, Transcription transcription) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Transcription(String text, String epdType) {
    }

    /** 전사(transcription) 결과인가. (config 응답 등 다른 타입은 제외) */
    public boolean isTranscription() {
        return responseType != null && responseType.contains("transcription") && transcription != null;
    }

    /** EPD가 끊은 확정 구간인가. epdType이 채워져 있으면 final. */
    public boolean isFinal() {
        return transcription != null
                && transcription.epdType() != null
                && !transcription.epdType().isBlank();
    }

    /** 인식된 텍스트. transcription이 없으면 null. */
    public String text() {
        return transcription != null ? transcription.text() : null;
    }
}
