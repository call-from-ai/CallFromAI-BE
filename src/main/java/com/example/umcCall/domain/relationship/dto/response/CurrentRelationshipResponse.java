package com.example.umcCall.domain.relationship.dto.response;

public record CurrentRelationshipResponse(
        String firstName,
        long relationshipDays,
        int totalCallCount,
        int callStreakDays,
        Long characterId
) {
}
