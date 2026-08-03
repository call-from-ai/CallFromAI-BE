package com.example.umcCall.domain.notification.entity;

import com.example.umcCall.domain.notification.enums.NotificationType;
import com.example.umcCall.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "activity_notification")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ActivityNotification extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "activity_notification_id")
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "relationship_id")
    private Long relationshipId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private NotificationType type;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "content", nullable = false, length = 500)
    private String content;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Builder
    public ActivityNotification(Long memberId, Long relationshipId, NotificationType type,
                                String title, String content) {
        this.memberId = memberId;
        this.relationshipId = relationshipId;
        this.type = type;
        this.title = title;
        this.content = content;
        this.read = false;
    }

    public void markAsRead() {
        this.read = true;
    }
}