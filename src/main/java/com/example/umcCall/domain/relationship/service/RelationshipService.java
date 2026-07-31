package com.example.umcCall.domain.relationship.service;

import com.example.umcCall.domain.ai.enums.CharacterSyncOperation;
import com.example.umcCall.domain.ai.service.CharacterSyncTaskService;
import com.example.umcCall.domain.character.entity.Character;
import com.example.umcCall.domain.character.enums.PreferTime;
import com.example.umcCall.domain.call.repository.CallRepository;
import com.example.umcCall.domain.member.entity.Member;
import com.example.umcCall.domain.member.exception.MemberErrorCode;
import com.example.umcCall.domain.member.repository.MemberRepository;
import com.example.umcCall.domain.relationship.dto.response.ContactPreferenceResponse;
import com.example.umcCall.domain.relationship.dto.response.CurrentRelationshipResponse;
import com.example.umcCall.domain.relationship.entity.Relationship;
import com.example.umcCall.domain.relationship.entity.RelationshipStatus;
import com.example.umcCall.domain.relationship.exception.RelationshipErrorCode;
import com.example.umcCall.domain.relationship.repository.RelationshipRepository;
import com.example.umcCall.domain.relationship.repository.RelationshipStatusRepository;
import com.example.umcCall.global.exception.BaseException;
import com.example.umcCall.domain.proactive.service.ProactiveScheduleCoordinator;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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
    private final CallRepository callRepository;
    private final MemberRepository memberRepository;
    private final CharacterSyncTaskService characterSyncTaskService;
    private final ProactiveScheduleCoordinator proactiveScheduleCoordinator;

    public CurrentRelationshipResponse getCurrentRelationship(Long memberId) {
        Relationship relationship = getCurrent(memberId);
        RelationshipStatus status = relationshipStatusRepository.findByRelationshipId(relationship.getId())
                .orElseThrow(() -> new BaseException(RelationshipErrorCode.RELATIONSHIP_STATUS_NOT_FOUND));
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BaseException(MemberErrorCode.MEMBER_NOT_FOUND));
        Character character = relationship.getCharacter();

        // 시작 당일은 1일차다. 서버/DB 시간대 경계로 시작일이 하루 앞서 저장돼도 0일을 노출하지 않는다.
        long relationshipDays = Math.max(
                1L,
                ChronoUnit.DAYS.between(relationship.getStartedAt(), LocalDate.now()) + 1);
        CallStatistics callStatistics = calculateCallStatistics(
                callRepository.findCompletedCallTimes(relationship.getId()), LocalDate.now());
        return new CurrentRelationshipResponse(
                member.getFirstName(),
                relationshipDays,
                callStatistics.totalCallCount(),
                callStatistics.callStreakDays(),
                character.getId()
        );
    }

    @Transactional
    public ContactPreferenceResponse updateContactPreference(Long memberId, PreferTime preferTime) {
        Relationship relationship = getCurrent(memberId);
        Character character = relationship.getCharacter();
        character.updatePreferTime(preferTime);
        proactiveScheduleCoordinator.reschedule(relationship);
        characterSyncTaskService.enqueue(character.getId(), CharacterSyncOperation.UPSERT);
        return new ContactPreferenceResponse(character.getPreferTime());
    }

    private Relationship getCurrent(Long memberId) {
        return relationshipRepository.findByMemberIdAndMainTrueAndCharacterDeletedAtIsNull(memberId)
                .orElseThrow(() -> new BaseException(RelationshipErrorCode.CURRENT_RELATIONSHIP_NOT_FOUND));
    }

    private CallStatistics calculateCallStatistics(List<LocalDateTime> completedCallTimes, LocalDate today) {
        if (completedCallTimes.isEmpty()) {
            return new CallStatistics(0, 0);
        }

        List<LocalDate> callDates = completedCallTimes.stream()
                .map(LocalDateTime::toLocalDate)
                .distinct()
                .toList();
        LocalDate latestCallDate = callDates.get(0);
        if (latestCallDate.isBefore(today.minusDays(1))) {
            return new CallStatistics(completedCallTimes.size(), 0);
        }

        int streakDays = 1;
        for (int i = 1; i < callDates.size(); i++) {
            if (!callDates.get(i).equals(callDates.get(i - 1).minusDays(1))) {
                break;
            }
            streakDays++;
        }
        return new CallStatistics(completedCallTimes.size(), streakDays);
    }

    private record CallStatistics(int totalCallCount, int callStreakDays) {
    }
}
