package com.example.umcCall.domain.ai.dto;

import java.util.List;

public record AiChatRequest(
        Long characterId,
        String message,
        AiCharacterSnapshot character,
        AiRelationshipSnapshot relationship,
        List<AiChatHistoryItem> history
) {
    public AiChatRequest {
        history = history == null ? List.of() : List.copyOf(history);
    }
}
