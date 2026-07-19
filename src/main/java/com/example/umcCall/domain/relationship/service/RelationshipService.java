package com.example.umcCall.domain.relationship.service;

import com.example.umcCall.domain.ai.enums.CharacterSyncOperation;
import com.example.umcCall.domain.ai.service.CharacterSyncTaskService;
import com.example.umcCall.domain.character.entity.Character;
import com.example.umcCall.domain.character.enums.PreferTime;
import com.example.umcCall.domain.relationship.dto.response.ContactPreferenceResponse;
import com.example.umcCall.domain.relationship.dto.response.CurrentRelationshipResponse;
import com.example.umcCall.domain.relationship.entity.Relationship;
import com.example.umcCall.domain.relationship.entity.RelationshipStatus;
import com.example.umcCall.domain.relationship.exception.RelationshipErrorCode;
import com.example.umcCall.domain.relationship.repository.RelationshipRepository;
import com.example.umcCall.domain.relationship.repository.RelationshipStatusRepository;
import com.example.umcCall.global.exception.BaseException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RelationshipService {

    private final RelationshipRepository relationshipRepository;
    private final RelationshipStatusRepository relationshipStatusRepository;
    private final CharacterSyncTaskService characterSyncTaskService;

    public CurrentRelationshipResponse getCurrentRelationship(Long memberId) {
        Relationship relationship = getCurrent(memberId);
        RelationshipStatus status = relationshipStatusRepository.findByRelationshipId(relationship.getId())
                .orElseThrow(() -> new BaseException(RelationshipErrorCode.RELATIONSHIP_STATUS_NOT_FOUND));
        Character character = relationship.getCharacter();

        long relationshipDays = ChronoUnit.DAYS.between(relationship.getStartedAt(), LocalDate.now()) + 1;
        return new CurrentRelationshipResponse(
                character.getFirstName(),
                relationshipDays,
                status.getTotalCallCount(),
                status.getCallStreakDays(),
                character.getId()
        );
    }

    @Transactional
    public ContactPreferenceResponse updateContactPreference(Long memberId, PreferTime preferTime) {
        Character character = getCurrent(memberId).getCharacter();
        character.updatePreferTime(preferTime);
        characterSyncTaskService.enqueue(character.getId(), CharacterSyncOperation.UPSERT);
        return new ContactPreferenceResponse(character.getPreferTime());
    }

    private Relationship getCurrent(Long memberId) {
        return relationshipRepository.findByMemberIdAndMainTrueAndCharacterDeletedAtIsNull(memberId)
                .orElseThrow(() -> new BaseException(RelationshipErrorCode.CURRENT_RELATIONSHIP_NOT_FOUND));
    }
}
