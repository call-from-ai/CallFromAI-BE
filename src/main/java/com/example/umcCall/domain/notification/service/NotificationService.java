package com.example.umcCall.domain.notification.service;

import com.example.umcCall.domain.member.repository.MemberRepository;
import com.example.umcCall.domain.notification.dto.response.NotificationResponse;
import com.example.umcCall.domain.notification.entity.ActivityNotification;
import com.example.umcCall.domain.notification.enums.NotificationType;
import com.example.umcCall.domain.notification.exception.NotificationErrorCode;
import com.example.umcCall.domain.notification.event.ActivityNotificationCreatedEvent;
import com.example.umcCall.domain.notification.repository.ActivityNotificationRepository;
import com.example.umcCall.domain.relationship.entity.Relationship;
import com.example.umcCall.domain.relationship.repository.RelationshipRepository;
import com.example.umcCall.global.exception.BaseException;
import com.example.umcCall.domain.character.entity.Character;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final int VISIBLE_DAYS = 7;
    private static final Set<Integer> MILESTONE_DAYS = Set.of(30, 50, 100, 200, 300, 365);

    private final ActivityNotificationRepository notificationRepository;
    private final RelationshipRepository relationshipRepository;
    private final MemberRepository memberRepository;
    private final ApplicationEventPublisher eventPublisher;

    // 조회
    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotifications(Long memberId) {
        LocalDateTime after = LocalDateTime.now().minusDays(VISIBLE_DAYS);
        List<ActivityNotification> notifications = notificationRepository
                .findByMemberIdAndCreatedAtAfterOrderByCreatedAtDesc(memberId, after);

        Set<Long> relationshipIds = notifications.stream()
                .map(ActivityNotification::getRelationshipId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Long -> Character로 변경
        Map<Long, Character> relationshipToCharacter = relationshipRepository.findAllById(relationshipIds).stream()
                .collect(Collectors.toMap(Relationship::getId, Relationship::getCharacter));

        return notifications.stream()
                .map(notification -> {
                    Character character = relationshipToCharacter.get(notification.getRelationshipId());
                    Long characterId = character != null ? character.getId() : null;
                    String characterImageUrl = character != null ? character.getImageUrl() : null;
                    return NotificationResponse.from(notification, characterId, characterImageUrl);
                })
                .toList();
    }

    // 부분 읽음 처리
    @Transactional
    public void markAsRead(Long memberId, Long notificationId) {
        ActivityNotification notification = notificationRepository.findByIdAndMemberId(notificationId, memberId)
                .orElseThrow(() -> new BaseException(NotificationErrorCode.NOTIFICATION_NOT_FOUND));
        notification.markAsRead();
    }

    // 기념일 알림 생성 (매일 자정 스케줄러)
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void createAnniversaryNotifications() {
        List<Relationship> relationships = relationshipRepository.findAllWithCharacterByCharacterDeletedAtIsNull();

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = todayStart.plusDays(1);

        // 오늘 이미 생성된 기념일 알림의 relationshipId를 한 번에 조회
        Set<Long> alreadyNotifiedRelationshipIds = notificationRepository
                .findByTypeAndCreatedAtBetween(NotificationType.ANNIVERSARY, todayStart, todayEnd)
                .stream()
                .map(ActivityNotification::getRelationshipId)
                .collect(Collectors.toSet());

        for (Relationship relationship : relationships) {
            if (alreadyNotifiedRelationshipIds.contains(relationship.getId())) {
                continue;
            }

            long daysTogether = ChronoUnit.DAYS.between(relationship.getStartedAt(), LocalDate.now()) + 1;

            if (MILESTONE_DAYS.contains((int) daysTogether)) {
                String content = String.format(
                        "오늘은 %s님과 함께한지 %d일 째! 작은 기념일을 함께 축하해요",
                        relationship.getCharacter().getFirstName(), daysTogether
                );

                notifyAndPush(relationship.getMemberId(), relationship.getId(),
                        NotificationType.ANNIVERSARY, "기념일", content);
            }
        }
    }

    // ===== 공통: 알림 저장 + FCM 발송 (방해금지/전체알림 설정 체크 포함) =====

    @Transactional
    public void notifyAndPush(Long memberId, Long relationshipId, NotificationType type, String title, String content) {
        ActivityNotification notification = notificationRepository.save(
                ActivityNotification.builder()
                        .memberId(memberId)
                        .relationshipId(relationshipId)
                        .type(type)
                        .title(title)
                        .content(content)
                        .build()
        );

        eventPublisher.publishEvent(
                new ActivityNotificationCreatedEvent(memberId, notification.getId(), title, content));
    }
}
