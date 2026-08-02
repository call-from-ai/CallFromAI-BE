package com.example.umcCall.domain.notification.listener;

import com.example.umcCall.domain.call.event.CallMissedEvent;
import com.example.umcCall.domain.notification.enums.NotificationType;
import com.example.umcCall.domain.notification.service.NotificationService;
import com.example.umcCall.domain.relationship.entity.Relationship;
import com.example.umcCall.domain.relationship.repository.RelationshipRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class CallMissedNotificationListener {

    private final NotificationService notificationService;
    private final RelationshipRepository relationshipRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCallMissed(CallMissedEvent event) {
        try {
            String characterName = relationshipRepository
                    .findByIdWithCharacter(event.relationshipId())
                    .map(relationship -> relationship.getCharacter().getFirstName())
                    .orElse("상대방");

            String content = String.format("%s에게서 받지 못한 전화가 있어요.", characterName);

            notificationService.notifyAndPush(
                    event.memberId(),
                    event.relationshipId(),
                    NotificationType.MISSED_CALL,
                    "부재중 전화",
                    content
            );
        } catch (Exception e) {
            log.warn("부재중 알림 생성 실패. callId={}, relationshipId={}", event.callId(), event.relationshipId(), e);
        }
    }
}