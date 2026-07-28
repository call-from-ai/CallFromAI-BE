package com.example.umcCall.domain.ai.dto;

import java.util.List;

public record AiSummaryRequest(
        Long relationshipId,
        String userName,
        String characterName,
        String previousSummary,
        List<AiSummaryMessage> messages,
        int maxCharacters
) {
    public AiSummaryRequest {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }
}
