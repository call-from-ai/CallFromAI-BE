package com.example.umcCall.domain.ai.dto;

import java.util.List;

public record AiCharacterSnapshot(
        Long characterId,
        String name,
        Integer age,
        String gender,
        String mbti,
        String responseStyle,
        String job,
        String lifeType,
        String preferTime,
        Integer romanceStyleScore,
        List<String> keywords,
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
