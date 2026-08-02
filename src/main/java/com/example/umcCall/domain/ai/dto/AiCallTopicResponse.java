package com.example.umcCall.domain.ai.dto;

/**
 * 통화 주제 라벨 응답.
 *
 * @param topic 한 문장짜리 주제 라벨(화자 중립, 요청한 {@code maxCharacters} 이내)
 */
public record AiCallTopicResponse(
        String topic
) {
}
