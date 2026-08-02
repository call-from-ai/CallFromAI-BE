package com.example.umcCall.domain.notification.dto.response;

import com.example.umcCall.domain.notification.entity.ActivityNotification;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long notificationId,
        String type,
        String title,
        String content,
        boolean read,
        LocalDateTime createdAt
) {
    public static NotificationResponse from(ActivityNotification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType().name(),
                notification.getTitle(),
                notification.getContent(),
                notification.isRead(),
                notification.getCreatedAt()
        );
    }
}