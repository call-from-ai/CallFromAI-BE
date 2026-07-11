package com.example.umcCall.domain.chat.entity;

import com.example.umcCall.domain.chat.enums.RoomType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 채팅방. 캐릭터 생성 시 함께 생성되는 채팅방(room_type=CHARACTER)을 우선 다룬다.
 */
@Entity
@Getter
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chat_room_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    private RoomType roomType;

    @Column(name = "is_muted")
    private boolean muted;

    @Column(name = "is_deleted")
    private boolean deleted;

    // 아직 메시지가 없으면 null
    private LocalDateTime lastMessageAt;

    @CreatedDate
    private LocalDateTime createdAt;

    private Long memberId;

    // MANAGER 타입 채팅방은 관계가 없을 수 있어 nullable
    private Long relationshipId;

    @Builder
    public ChatRoom(RoomType roomType, Long memberId, Long relationshipId) {
        this.roomType = roomType;
        this.memberId = memberId;
        this.relationshipId = relationshipId;
        this.muted = false;
        this.deleted = false;
    }
}
