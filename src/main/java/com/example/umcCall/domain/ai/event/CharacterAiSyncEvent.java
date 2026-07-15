package com.example.umcCall.domain.ai.event;

public sealed interface CharacterAiSyncEvent {

    Long characterId();

    record Upsert(Long characterId) implements CharacterAiSyncEvent {
    }

    record Delete(Long characterId) implements CharacterAiSyncEvent {
    }
}
