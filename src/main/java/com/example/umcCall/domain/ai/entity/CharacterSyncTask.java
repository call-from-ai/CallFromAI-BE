package com.example.umcCall.domain.ai.entity;

import com.example.umcCall.domain.ai.enums.CharacterSyncOperation;
import com.example.umcCall.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "character_sync_task")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CharacterSyncTask extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long characterId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CharacterSyncOperation operation;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false)
    private LocalDateTime nextAttemptAt;

    private LocalDateTime completedAt;

    @Column(length = 1000)
    private String lastError;

    private CharacterSyncTask(Long characterId, CharacterSyncOperation operation) {
        this.characterId = characterId;
        this.operation = operation;
        this.nextAttemptAt = LocalDateTime.now();
    }

    public static CharacterSyncTask pending(Long characterId, CharacterSyncOperation operation) {
        return new CharacterSyncTask(characterId, operation);
    }

    public void complete() {
        this.completedAt = LocalDateTime.now();
        this.lastError = null;
    }

    public void retry(RuntimeException exception) {
        attempts++;
        long delayMinutes = Math.min(60, 1L << Math.min(attempts - 1, 6));
        nextAttemptAt = LocalDateTime.now().plusMinutes(delayMinutes);
        String message = exception.getMessage();
        lastError = message == null ? exception.getClass().getSimpleName()
                : message.substring(0, Math.min(message.length(), 1000));
    }
}
