package com.example.umcCall.domain.ai.dto;

import java.util.List;

public record AiProactiveRequest(
        String requestId,
        Long characterId,
        String message,
        String contactReason,
        String relationshipState,
        String recentResponse,
        AiCharacterSnapshot character,
        AiRelationshipSnapshot relationship,
        List<AiChatHistoryItem> history
) {
    public AiProactiveRequest {
        history = history == null ? List.of() : List.copyOf(history);
    }
}
