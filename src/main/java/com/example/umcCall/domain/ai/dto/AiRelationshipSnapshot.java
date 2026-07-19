package com.example.umcCall.domain.ai.dto;

public record AiRelationshipSnapshot(
        Long relationshipId,
        String relationshipStage,
        String emotion,
        String speechStyle,
        Integer spiceLevel,
        long daysTogether,
        Long version
) {
}
