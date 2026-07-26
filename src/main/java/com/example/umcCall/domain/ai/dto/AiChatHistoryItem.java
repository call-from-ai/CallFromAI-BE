package com.example.umcCall.domain.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

/**
 * AI 서버 계약의 대화 이력 항목. 원본 필드명 {@code sender}를 유지하되 JSON 키만 계약값 {@code "role"}로 보낸다.
 * 서버는 {@code role}(비어있으면 400)·{@code content}만 쓰고 {@code createdAt}은 무시한다.
 *
 * @param sender "user" | "assistant" (JSON 키는 "role")
 */
public record AiChatHistoryItem(
        @JsonProperty("role") String sender,
        String content,
        LocalDateTime createdAt
) {
}
