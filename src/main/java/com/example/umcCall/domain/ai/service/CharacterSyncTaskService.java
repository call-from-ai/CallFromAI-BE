package com.example.umcCall.domain.ai.service;

import com.example.umcCall.domain.ai.client.AiServerClient;
import com.example.umcCall.domain.ai.entity.CharacterSyncTask;
import com.example.umcCall.domain.ai.enums.CharacterSyncOperation;
import com.example.umcCall.domain.ai.mapper.AiCharacterSnapshotMapper;
import com.example.umcCall.domain.ai.repository.CharacterSyncTaskRepository;
import com.example.umcCall.domain.character.repository.CharacterAiProfileRepository;
import com.example.umcCall.domain.character.repository.CharacterRepository;
import com.example.umcCall.domain.relationship.repository.RelationshipRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class CharacterSyncTaskService {

    private final CharacterSyncTaskRepository taskRepository;
    private final AiServerClient aiServerClient;
    private final CharacterRepository characterRepository;
    private final CharacterAiProfileRepository profileRepository;
    private final RelationshipRepository relationshipRepository;
    private final AiCharacterSnapshotMapper snapshotMapper;

    @Transactional
    public void enqueue(Long characterId, CharacterSyncOperation operation) {
        taskRepository.save(CharacterSyncTask.pending(characterId, operation));
    }

    @Scheduled(fixedDelayString = "${ai.character-sync-delay-ms:30000}")
    public void retryPendingTasks() {
        taskRepository.findTop50ByCompletedAtIsNullAndNextAttemptAtLessThanEqualOrderById(LocalDateTime.now())
                .forEach(task -> {
                    try {
                        process(task.getId());
                    } catch (RuntimeException exception) {
                        log.error("AI 캐릭터 동기화 작업 실패. taskId={}", task.getId(), exception);
                    }
                });
    }

    public void process(Long taskId) {
        CharacterSyncTask task = taskRepository.findById(taskId).orElseThrow();
        if (task.getCompletedAt() != null) return;
        try {
            if (task.getOperation() == CharacterSyncOperation.DELETE) {
                aiServerClient.deleteCharacterData(task.getCharacterId());
            } else {
                var character = characterRepository.findById(task.getCharacterId()).orElseThrow();
                var profile = profileRepository.findById(task.getCharacterId()).orElseThrow();
                var relationship = relationshipRepository.findByCharacterId(task.getCharacterId()).orElseThrow();
                aiServerClient.syncCharacter(snapshotMapper.toSnapshot(character, profile, relationship));
            }
            task.complete();
            taskRepository.save(task);
        } catch (RuntimeException exception) {
            task.retry(exception);
            taskRepository.save(task);
            throw exception;
        }
    }
}
