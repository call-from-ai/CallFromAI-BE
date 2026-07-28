package com.example.umcCall.domain.notification.service;

import com.example.umcCall.domain.notification.dto.response.NotificationResponse;
import com.example.umcCall.domain.notification.entity.SystemNotification;
import com.example.umcCall.domain.notification.enums.NotificationType;
import com.example.umcCall.domain.notification.exception.NotificationErrorCode;
import com.example.umcCall.domain.notification.repository.SystemNotificationRepository;
import com.example.umcCall.domain.relationship.entity.Relationship;
import com.example.umcCall.domain.relationship.repository.RelationshipRepository;
import com.example.umcCall.global.exception.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final int VISIBLE_DAYS = 7;
    private static final Set<Integer> MILESTONE_DAYS = Set.of(30, 50, 100, 200, 300, 365);

    private final SystemNotificationRepository notificationRepository;
    private final RelationshipRepository relationshipRepository;

    // 조회
    public List<NotificationResponse> getNotifications(Long memberId) {
        LocalDateTime after = LocalDateTime.now().minusDays(VISIBLE_DAYS);
        return notificationRepository.findByMemberIdAndOccurredAtAfterOrderByOccurredAtDesc(memberId, after)
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    // 읽음 처리
    @Transactional
    public void markAsRead(Long memberId, Long notificationId) {
        SystemNotification notification = notificationRepository.findByIdAndMemberId(notificationId, memberId)
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
                .findByTypeAndOccurredAtBetween(NotificationType.ANNIVERSARY, todayStart, todayEnd)
                .stream()
                .map(SystemNotification::getRelationshipId)
                .collect(Collectors.toSet());

        List<SystemNotification> newNotifications = new ArrayList<>();

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

                newNotifications.add(
                        SystemNotification.builder()
                                .memberId(relationship.getMemberId())
                                .relationshipId(relationship.getId())
                                .type(NotificationType.ANNIVERSARY)
                                .title("기념일")
                                .content(content)
                                .occurredAt(LocalDateTime.now())
                                .build()
                );
            }
        }

        notificationRepository.saveAll(newNotifications);
    }
}