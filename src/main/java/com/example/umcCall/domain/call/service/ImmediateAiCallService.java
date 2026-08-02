package com.example.umcCall.domain.call.service;

import com.example.umcCall.domain.call.entity.Call;
import com.example.umcCall.domain.call.enums.CallSender;
import com.example.umcCall.domain.call.enums.CallStatus;
import com.example.umcCall.domain.call.event.CallRingingEvent;
import com.example.umcCall.domain.call.repository.CallRepository;
import com.example.umcCall.domain.character.entity.Character;
import com.example.umcCall.domain.relationship.entity.Relationship;
import com.example.umcCall.domain.relationship.repository.RelationshipRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 예약 없이 AI 착신 통화를 즉시 생성한다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ImmediateAiCallService {

    private final RelationshipRepository relationshipRepository;
    private final CallRepository callRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 호출 가능한 관계이고 진행 중인 통화가 없으면 RINGING 통화를 만든다.
     *
     * @return 통화를 만들었으면 true, 관계가 유효하지 않거나 이미 통화 중이면 false
     */
    @Transactional
    public boolean ring(Long relationshipId) {
        Relationship relationship = relationshipRepository.findByIdForUpdate(relationshipId).orElse(null);
        if (relationship == null
                || !relationship.isMain()
                || relationship.getCharacter().getDeletedAt() != null) {
            return false;
        }

        if (callRepository.existsByRelationshipIdAndStatusIn(relationshipId, CallStatus.ACTIVE)) {
            log.info("선제 AI 발신 생략(이미 진행 중인 통화 있음). relationshipId={}", relationshipId);
            return false;
        }

        Call call = callRepository.save(Call.builder()
                .relationship(relationship)
                .sender(CallSender.AI)
                .build());
        log.info("선제 AI 즉시 발신. callId={}, relationshipId={}", call.getId(), relationshipId);

        // 알림 도메인이 착신 푸시를 보낸다(AFTER_COMMIT 수신).
        Character character = relationship.getCharacter();
        eventPublisher.publishEvent(new CallRingingEvent(
                call.getId(),
                relationshipId,
                relationship.getMemberId(),
                character.getId(),
                character.getFirstName(),
                character.getImageUrl()));
        return true;
    }
}
