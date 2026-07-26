package com.example.umcCall.domain.chat.entity;

import com.example.umcCall.domain.chat.enums.MessageType;
import com.example.umcCall.domain.chat.enums.SenderType;
import com.example.umcCall.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 채팅 메시지
 */
@Entity
@Getter
@Table(name = "chat_message")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage extends BaseTimeEntity {

    // TODO(AI 연동): ai_status, in_reply_to_id 컬럼 추가 필요.
    // AiConversationService.chat() 호출 결과를 여기에 저장.

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chat_message_id")
    private Long id;

    /** 발신자 타입(USER | AI | SYSTEM). */
    @Enumerated(EnumType.STRING)
    @Column(name = "sender_type", nullable = false)
    private SenderType senderType;

    /** 메시지 본문. 사진만 있는 메시지면 null일 수 있다. */
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    /** 메시지 타입(TEXT | IMAGE | TEXT_IMAGE). */
    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false)
    private MessageType messageType;

    /** 읽음 여부. 채팅방 목록 조회할때에 사용된다. */
    @Column(name = "is_read", nullable = false)
    private boolean read;

    /** 소프트삭제 여부. true면 유저 노출 조회에서 제외한다. */
    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    /** 소속 채팅방. 같은 도메인이므로 지연로딩 @ManyToOne으로 연관. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_room_id", nullable = false)
    private ChatRoom chatRoom;

    /** 선제 연락 멱등성 키. 일반 메시지는 null이다. */
    @Column(name = "proactive_request_id", unique = true, length = 80)
    private String proactiveRequestId;

    @Builder
    private ChatMessage(SenderType senderType, String content, MessageType messageType,
                        boolean read, boolean deleted, ChatRoom chatRoom, String proactiveRequestId) {
        this.senderType = senderType;
        this.content = content;
        this.messageType = messageType;
        this.read = read;
        this.deleted = deleted;
        this.chatRoom = chatRoom;
        this.proactiveRequestId = proactiveRequestId;
    }
}
