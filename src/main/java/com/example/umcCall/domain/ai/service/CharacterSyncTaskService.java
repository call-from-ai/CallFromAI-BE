package com.example.umcCall.domain.ai.service;

import com.example.umcCall.domain.ai.client.AiServerClient;
import com.example.umcCall.domain.ai.entity.CharacterSyncTask;
import com.example.umcCall.domain.ai.enums.CharacterSyncOperation;
import com.example.umcCall.domain.ai.event.CharacterAiSyncEvent;
import com.example.umcCall.domain.ai.mapper.AiCharacterSnapshotMapper;
import com.example.umcCall.domain.ai.repository.CharacterSyncTaskRepository;
import com.example.umcCall.domain.character.repository.CharacterAiProfileRepository;
import com.example.umcCall.domain.character.repository.CharacterRepository;
import com.example.umcCall.domain.character.service.CharacterHardDeleteService;
import com.example.umcCall.domain.relationship.repository.RelationshipRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher eventPublisher;
    private final CharacterHardDeleteService hardDeleteService;

    @Transactional
    public void enqueue(Long characterId, CharacterSyncOperation operation) {
        taskRepository.save(CharacterSyncTask.pending(characterId, operation));
        // 커밋 직후 즉시 AI 서버에 반영한다. 아래 영속 작업은 AI 서버 장애 시
        // 스케줄러가 재시도하기 위한 안전망이며, AI API는 같은 요청에 멱등적이다.
        if (operation == CharacterSyncOperation.DELETE) {
            eventPublisher.publishEvent(new CharacterAiSyncEvent.Delete(characterId));
        } else {
            eventPublisher.publishEvent(new CharacterAiSyncEvent.Upsert(characterId));
        }
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
                hardDeleteService.purge(task.getCharacterId());
            } else {
                var character = characterRepository.findById(task.getCharacterId()).orElse(null);
                // 삭제가 완료된 캐릭터에 대해 과거 UPSERT 작업이 남아 있을 수 있다.
                if (character == null) {
                    task.complete();
                    taskRepository.save(task);
                    return;
                }
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
