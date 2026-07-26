package com.example.umcCall.domain.chat.entity;

import com.example.umcCall.domain.chat.enums.RoomType;
import com.example.umcCall.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 채팅방. 방 생성 자체는 캐릭터 생성(용환님 도메인)에서 수행하고,
 * 채팅 도메인은 이미 만들어진 방을 받아 조회/전송/상태관리를 담당한다.
 * 외부 도메인(member, relationship)으로 나가는 FK는 지금은 Long 값으로만 보유한다.
 * 상대 엔티티가 만들어지면 이 파일만 수정해 @ManyToOne으로 승격한다.
 */
@Entity
@Getter
@Table(name = "chat_room")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoom extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chat_room_id")
    private Long id;

    /** 방 종류(CHARACTER | MANAGER). 목록 조회는 CHARACTER만 노출한다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "room_type", nullable = false)
    private RoomType roomType;

    /** 음소거 여부. true면 AI 발신 시 FCM 푸시를 스킵한다. */
    @Column(name = "is_muted", nullable = false)
    private boolean muted;

    /** 목록에서 숨김여부. 목록 노출만 제어한다. */
    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    /**
     * 이 id를 초과하는 메시지만 유저에게 노출한다.
     * null이면 제한 없이 전체 노출. 나가기 시 현재 마지막 메시지 id로 채워진다.
     */
    @Column(name = "message_visible_after_id")
    private Long messageVisibleAfterId;

    /** 마지막 메시지 시각. 목록 정렬의 키로 사용한다. */
    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    /** 사용자 FK. Member 엔티티 확정 시 @ManyToOne으로 승격 예정. */
    @Column(name = "member_id", nullable = false)
    private Long memberId;

    /** 관계 FK. MANAGER 방은 null. Relationship 엔티티 확정 시 @ManyToOne으로 승격 예정. */
    @Column(name = "relationship_id")
    private Long relationshipId;

    @Builder
    private ChatRoom(RoomType roomType, boolean muted, boolean deleted,
                     Long messageVisibleAfterId, LocalDateTime lastMessageAt,
                     Long memberId, Long relationshipId) {
        this.roomType = roomType;
        this.muted = muted;
        this.deleted = deleted;
        this.messageVisibleAfterId = messageVisibleAfterId;
        this.lastMessageAt = lastMessageAt;
        this.memberId = memberId;
        this.relationshipId = relationshipId;
    }

    public void archive() {
        this.deleted = true;
    }

    /** 음소거 여부를 요청받은 값으로 */
    public void updateMuted(boolean muted) {
        this.muted = muted;
    }

    /** 새 메시지가 오가면 마지막 메시지 시각을 갱신한다(목록 정렬 키). */
    public void updateLastMessageAt(LocalDateTime lastMessageAt) {
        this.lastMessageAt = lastMessageAt;

    }
}
