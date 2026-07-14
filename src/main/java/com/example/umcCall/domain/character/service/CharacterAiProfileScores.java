package com.example.umcCall.domain.character.service;

public record CharacterAiProfileScores(
        String mind,
        String responseStyle,
        String lifeType,
        int romanceStyleScore,
        int humor,
        int playfulness,
        int affection,
        int empathy,
        int attachment,
        int jealousy,
        int dominance,
        int confidence,
        int expressiveness,
        int emotionalStability
) {
    public static CharacterAiProfileScores baseline() {
        return new CharacterAiProfileScores(
                null, null, null, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
        );
    }
}
