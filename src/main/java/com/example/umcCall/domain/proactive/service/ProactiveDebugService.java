package com.example.umcCall.domain.proactive.service;

import com.example.umcCall.domain.character.repository.CharacterAiProfileRepository;
import com.example.umcCall.domain.proactive.dto.ProactiveProcessResponse;
import com.example.umcCall.domain.proactive.dto.ProactiveScheduleResponse;
import com.example.umcCall.domain.proactive.entity.ProactiveContactSchedule;
import com.example.umcCall.domain.proactive.enums.ProactiveAction;
import com.example.umcCall.domain.proactive.repository.ProactiveContactScheduleRepository;
import com.example.umcCall.domain.relationship.entity.Relationship;
import com.example.umcCall.domain.relationship.repository.RelationshipRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProactiveDebugService {

    private final RelationshipRepository relationshipRepository;
    private final ProactiveContactScheduleRepository scheduleRepository;
    private final CharacterAiProfileRepository profileRepository;
    private final ProactiveScheduleCoordinator coordinator;
    private final ProactiveContactProcessor processor;

    @Transactional
    public ProactiveScheduleResponse getStatus(Long memberId) {
        Relationship relationship = currentRelationship(memberId);
        coordinator.create(relationship);
        ProactiveContactSchedule schedule = scheduleRepository.findByRelationshipId(relationship.getId())
                .orElseThrow(() -> new IllegalStateException("Proactive schedule not found"));
        Double attachment = profileRepository.findById(relationship.getCharacter().getId())
                .map(profile -> profile.getAttachment())
                .orElse(null);
        return ProactiveScheduleResponse.of(schedule, attachment);
    }

    @Transactional
    public ProactiveScheduleResponse reschedule(Long memberId) {
        Relationship relationship = currentRelationship(memberId);
        coordinator.reschedule(relationship);
        return getStatus(memberId);
    }

    @Transactional
    public ProactiveScheduleResponse forceDue(Long memberId) {
        Relationship relationship = currentRelationship(memberId);
        coordinator.create(relationship);
        ProactiveContactSchedule schedule = scheduleRepository.findByRelationshipId(relationship.getId())
                .orElseThrow(() -> new IllegalStateException("Proactive schedule not found"));
        schedule.forceDue(LocalDateTime.now());
        return ProactiveScheduleResponse.of(schedule,
                profileRepository.findById(relationship.getCharacter().getId())
                        .map(profile -> profile.getAttachment())
                        .orElse(null));
    }

    public ProactiveProcessResponse processNow(Long memberId) {
        Relationship relationship = currentRelationship(memberId);
        coordinator.create(relationship);
        ProactiveContactSchedule schedule = scheduleRepository.findByRelationshipId(relationship.getId())
                .orElseThrow(() -> new IllegalStateException("Proactive schedule not found"));
        ProactiveContactProcessor.Claim claim = processor.claim(schedule.getId(), LocalDateTime.now());
        if (claim == null) {
            return ProactiveProcessResponse.skipped("POLICY_DEFERRED_OR_BLOCKED");
        }
        return executeClaim(claim);
    }

    public ProactiveProcessResponse forceSend(Long memberId) {
        Relationship relationship = currentRelationship(memberId);
        coordinator.create(relationship);
        ProactiveContactSchedule schedule = scheduleRepository.findByRelationshipId(relationship.getId())
                .orElseThrow(() -> new IllegalStateException("Proactive schedule not found"));
        ProactiveContactProcessor.Claim claim = processor.forceClaimForDebug(schedule.getId());
        if (claim == null) {
            return ProactiveProcessResponse.skipped("DISABLED_OR_INACTIVE");
        }
        return executeClaim(claim);
    }

    private ProactiveProcessResponse executeClaim(ProactiveContactProcessor.Claim claim) {
        try {
            if (claim.action() == ProactiveAction.CALL) {
                return processor.completeCall(claim, LocalDateTime.now())
                        ? ProactiveProcessResponse.callRinging(claim.requestId())
                        : ProactiveProcessResponse.skipped("CALL_NOT_CREATED");
            }
            String reply = processor.generate(claim);
            if (reply == null) {
                return ProactiveProcessResponse.skipped("CLAIM_CANCELED");
            }
            processor.complete(claim, reply, LocalDateTime.now());
            return ProactiveProcessResponse.completed(claim.requestId());
        } catch (RuntimeException exception) {
            processor.fail(claim, exception, LocalDateTime.now());
            throw exception;
        }
    }

    private Relationship currentRelationship(Long memberId) {
        return relationshipRepository.findByMemberIdAndMainTrueAndCharacterDeletedAtIsNull(memberId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Active relationship not found for memberId=" + memberId));
    }
}
