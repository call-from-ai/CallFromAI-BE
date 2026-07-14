package com.example.umcCall.domain.ai.service;

import com.example.umcCall.domain.ai.client.AiServerClient;
import com.example.umcCall.domain.ai.event.CharacterAiSyncEvent;
import com.example.umcCall.domain.ai.mapper.AiCharacterSnapshotMapper;
import com.example.umcCall.domain.character.entity.Character;
import com.example.umcCall.domain.character.entity.CharacterAiProfile;
import com.example.umcCall.domain.character.exception.CharacterErrorCode;
import com.example.umcCall.domain.character.repository.CharacterAiProfileRepository;
import com.example.umcCall.domain.character.repository.CharacterRepository;
import com.example.umcCall.domain.relationship.entity.Relationship;
import com.example.umcCall.domain.relationship.repository.RelationshipRepository;
import com.example.umcCall.global.exception.BaseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
@Slf4j
@RequiredArgsConstructor
public class AiCharacterSyncService {

    private final AiServerClient aiServerClient;
    private final CharacterRepository characterRepository;
    private final CharacterAiProfileRepository characterAiProfileRepository;
    private final RelationshipRepository relationshipRepository;
    private final AiCharacterSnapshotMapper snapshotMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void sync(CharacterAiSyncEvent.Upsert event) {
        try {
            Character character = characterRepository.findById(event.characterId())
                    .orElseThrow(() -> new BaseException(CharacterErrorCode.CHARACTER_NOT_FOUND));
            CharacterAiProfile profile = characterAiProfileRepository.findById(event.characterId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Character AI profile not found: " + event.characterId()));
            Relationship relationship = relationshipRepository.findByCharacterId(event.characterId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Relationship not found for character: " + event.characterId()));

            aiServerClient.syncCharacter(snapshotMapper.toSnapshot(character, profile, relationship));
        } catch (RuntimeException exception) {
            // TODO(AI 연동): outbox 또는 재시도 큐로 전환해 커밋 이후 동기화 실패를 복구할 것.
            log.error("AI 캐릭터 snapshot 동기화 실패. characterId={}", event.characterId(), exception);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void cleanup(CharacterAiSyncEvent.Delete event) {
        try {
            aiServerClient.deleteCharacterData(event.characterId());
        } catch (RuntimeException exception) {
            // TODO(AI 연동): outbox 또는 재시도 큐로 전환해 cleanup 실패를 복구할 것.
            log.error("AI 캐릭터 파생 데이터 cleanup 실패. characterId={}", event.characterId(), exception);
        }
    }
}
