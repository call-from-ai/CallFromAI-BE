package com.example.umcCall.domain.call.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Set;

/**
 * CLOVA {@code recognize} 응답({@code NestResponse.contents}) JSON 파싱 결과.
 * partial/final은 {@code transcription.epdType}로 판정한다.
 * <p><b>final = 턴 끝</b>: 침묵({@code gap})·스트림 끝({@code endPoint})·무음 폴백({@code unvoice})만 final이다.
 * {@code durationThreshold}/{@code syllableThreshold}/{@code period}는 발화 중간의 길이·문장 토막이라 partial로 둔다
 * (이걸 final로 치면 한 턴이 조각조각 LLM/전사로 넘어간다). CONFIG는 gap 기반으로 튜닝됨({@code semanticEpd.gapThreshold}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NestRecognizeResult(List<String> responseType, Transcription transcription) {

    /** 턴이 끝났음을 뜻하는 epdType. 이 값들만 final. (gap=침묵, endPoint=스트림 끝, unvoice=10s 무음 폴백) */
    private static final Set<String> TURN_END_EPD_TYPES = Set.of("gap", "endPoint", "unvoice");

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Transcription(String text, String epdType) {
    }

    /** 전사(transcription) 결과인가. (config 응답 등 다른 타입은 제외) */
    public boolean isTranscription() {
        return responseType != null && responseType.contains("transcription") && transcription != null;
    }

    /** 턴이 끝난 확정 구간인가. */
    public boolean isFinal() {
        return transcription != null
                && transcription.epdType() != null
                && TURN_END_EPD_TYPES.contains(transcription.epdType());
    }

    /** 인식된 텍스트. transcription이 없으면 null. */
    public String text() {
        return transcription != null ? transcription.text() : null;
    }
}
