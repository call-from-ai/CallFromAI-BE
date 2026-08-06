package com.example.umcCall.domain.ai.dto;

import com.example.umcCall.domain.ai.enums.AiConversationChannel;
import java.time.OffsetDateTime;
import java.util.List;

public record AiProactiveRequest(
        String requestId,
        Long characterId,
        AiConversationChannel channel,
        String message,
        String contactReason,
        String relationshipState,
        String recentResponse,
        String userName,
        String userTimeZone,
        OffsetDateTime localDateTime,
        AiCharacterSnapshot character,
        AiRelationshipSnapshot relationship,
        List<AiChatHistoryItem> history
) {
    public AiProactiveRequest {
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
        history = history == null ? List.of() : List.copyOf(history);
    }
}
