package com.example.umcCall.domain.ai.dto;

public record AiRelationshipSnapshot(
        Long relationshipId,
        String relationshipStage,
        Integer closeness,
        Integer conflictLevel,
        String emotion,
        String speechStyle,
        Integer spiceLevel,
        long daysTogether,
        Long version
) {
}
