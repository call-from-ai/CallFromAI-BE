package com.example.umcCall.domain.ai.dto;

import com.example.umcCall.domain.ai.enums.AiConversationChannel;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * AI 서버 {@code POST /chat} 요청 바디.
 * <p>{@code requestId}는 계약 문서엔 "선택"이나 실제 서버는 <b>필수</b>다(없으면 400). 멱등성 키라 매 요청 고유값을 쓴다.
 */
public record AiChatRequest(
        String requestId,
        Long characterId,
        AiConversationChannel channel,
        String userName,
        String userTimeZone,
        @JsonSerialize(using = ToStringSerializer.class)
        OffsetDateTime localDateTime,
        AiUserSnapshot user,
        String message,
        AiCharacterSnapshot character,
        AiRelationshipSnapshot relationship,
        List<AiChatHistoryItem> history
) {
    public AiChatRequest {
        if (channel == null) {
            throw new IllegalArgumentException("channel is required");
        }
        if (userName == null || userName.isBlank()) {
            throw new IllegalArgumentException("userName is required");
        }
        if (userTimeZone == null || userTimeZone.isBlank()) {
            throw new IllegalArgumentException("userTimeZone is required");
        }
        if (localDateTime == null) {
            throw new IllegalArgumentException("localDateTime is required");
        }

        history = history == null
                ? List.of()
                : List.copyOf(history);
    }
}
