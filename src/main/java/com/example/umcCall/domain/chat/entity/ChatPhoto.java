package com.example.umcCall.domain.chat.entity;

import com.example.umcCall.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 채팅 메시지에 첨부된 사진(메시지당 1장).
 * chat_message_id에 UNIQUE를 걸어 "메시지당 1장"을 DB 레벨에서 보장한다.
 * FK는 사진 쪽이 소유해, 사진 없는 메시지가 대부분인 메시지 테이블은 가볍게 유지한다.
 */
@Entity
@Getter
@Table(name = "chat_photo")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatPhoto extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chat_photo_id")
    private Long id;

    /** 소속 메시지. 메시지당 1장이라 UNIQUE. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_message_id", nullable = false, unique = true)
    private ChatMessage chatMessage;

    /**
     * S3에 저장된 사진의 객체 key</b>(공개 URL이 아님) 유저에게 내려줄 땐 presigned URL로 발급한다.
     */
    @Column(name = "photo_url", nullable = false, length = 512)
    private String photoUrl;

    @Builder
    private ChatPhoto(ChatMessage chatMessage, String photoUrl) {
        this.chatMessage = chatMessage;
        this.photoUrl = photoUrl;
    }
}
