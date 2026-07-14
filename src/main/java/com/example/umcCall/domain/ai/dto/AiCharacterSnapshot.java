package com.example.umcCall.domain.ai.dto;

public record AiCharacterSnapshot(
        Long characterId,
        String name,
        String responseStyle,
        String job,
        String lifeType,
        Integer romanceStyleScore,
        TraitProfile traits
) {
    public record TraitProfile(
            Integer humor,
            Integer playfulness,
            Integer affection,
            Integer empathy,
            Integer attachment,
            Integer jealousy,
            Integer dominance,
            Integer confidence,
            Integer expressiveness,
            Integer emotionalStability,
            Integer calculationVersion
    ) {
    }
}
