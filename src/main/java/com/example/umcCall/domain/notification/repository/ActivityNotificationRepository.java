package com.example.umcCall.domain.notification.repository;

import com.example.umcCall.domain.notification.entity.ActivityNotification;
import com.example.umcCall.domain.notification.enums.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ActivityNotificationRepository extends JpaRepository<ActivityNotification, Long> {

    // 최근 7일간의 알림만
    List<ActivityNotification> findByMemberIdAndCreatedAtAfterOrderByCreatedAtDesc(
            Long memberId, LocalDateTime after);

    Optional<ActivityNotification> findByIdAndMemberId(Long notificationId, Long memberId);

    boolean existsByMemberIdAndRelationshipIdAndTypeAndContent(
            Long memberId, Long relationshipId, NotificationType type, String content);

    List<ActivityNotification> findByMemberIdAndReadFalse(Long memberId);

    List<ActivityNotification> findByTypeAndCreatedAtBetween(
            NotificationType type, LocalDateTime start, LocalDateTime end);

    void deleteByMemberId(Long memberId);
}