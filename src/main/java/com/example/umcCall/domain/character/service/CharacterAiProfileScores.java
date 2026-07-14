package com.example.umcCall.domain.character.service;

public record CharacterAiProfileScores(
        String lifeType,
        double humor,
        double playfulness,
        double affection,
        double empathy,
        double attachment,
        double jealousy,
        double dominance,
        double confidence,
        double expressiveness,
        double emotionalStability
) {
    public static CharacterAiProfileScores baseline(String lifeType) {
        return new CharacterAiProfileScores(
                lifeType, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3
        );
    }
}
