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
        List<TraitItem> traits
) {
    public AiCharacterSnapshot {
        traits = traits == null ? List.of() : List.copyOf(traits);
    }

    public record TraitItem(String name, Integer priority) {
    }
}
