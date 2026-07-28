package com.example.umcCall.domain.notification.repository;

import com.example.umcCall.domain.notification.entity.SystemNotification;
import com.example.umcCall.domain.notification.enums.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SystemNotificationRepository extends JpaRepository<SystemNotification, Long> {

    // 최근 7일간의 알림만
    List<SystemNotification> findByMemberIdAndOccurredAtAfterOrderByOccurredAtDesc(
            Long memberId, LocalDateTime after);

    Optional<SystemNotification> findByIdAndMemberId(Long notificationId, Long memberId);

    boolean existsByMemberIdAndRelationshipIdAndTypeAndContent(
            Long memberId, Long relationshipId, NotificationType type, String content);

    List<SystemNotification> findByTypeAndOccurredAtBetween(
            NotificationType type, LocalDateTime start, LocalDateTime end);
}