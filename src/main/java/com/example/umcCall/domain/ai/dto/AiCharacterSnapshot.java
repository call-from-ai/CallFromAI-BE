package com.example.umcCall.domain.ai.dto;

import java.util.List;

public record AiCharacterSnapshot(
        Long characterId,
        String name,
        String gender,
        Integer age,
        String job,
        String preferTime,
        String mbti,
        String mind,
        String responseStyle,
        String lifeType,
        Integer romanceStyleScore,
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
        Integer calculationVersion,
        List<TraitItem> traits
) {
    public AiCharacterSnapshot {
        traits = traits == null ? List.of() : List.copyOf(traits);
    }

    public record TraitItem(String name, Integer priority) {
    }
}
