package com.example.umcCall.domain.ai.dto;

public record AiCharacterSnapshot(
        Long characterId,
        String name,
        String job,
        String lifeType,
        TraitProfile traits
) {
    public record TraitProfile(
            Double humor,
            Double playfulness,
            Double affection,
            Double empathy,
            Double attachment,
            Double jealousy,
            Double dominance,
            Double confidence,
            Double expressiveness,
            Double emotionalStability,
            Integer calculationVersion
    ) {
    }
}
