package com.example.umcCall.domain.call.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * CLOVA {@code recognize} 응답({@code NestResponse.contents}) JSON 파싱 결과.
 * partial/final은 {@code transcription.epdType}로 판정: 값이 채워지면(EPD가 끊음) final, 비면 partial.
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
