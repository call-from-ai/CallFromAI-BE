package com.example.umcCall.domain.chat.dto.response;

import com.example.umcCall.domain.chat.entity.ChatMessage;
import com.example.umcCall.domain.chat.enums.MessageType;
import com.example.umcCall.domain.chat.enums.SenderType;
import java.time.LocalDateTime;
import lombok.Builder;

/**
 * 채팅 메시지 1건 응답.
 * photoUrl은 사진이 없는 메시지면 null.
 */
@Builder
public record ChatMessageResponse(
        Long chatMessageId,
        SenderType senderType,
        String content,
        MessageType messageType,
        String photoUrl,
        LocalDateTime createdAt
) {
    /** 사진 없는 메시지용(photoUrl=null). */
    public static ChatMessageResponse from(ChatMessage message) {
        return from(message, null);
    }

    /** 사진 URL을 함께 담는다(전송 응답·조회에서 chat_photo 조인 결과 전달용). */
    public static ChatMessageResponse from(ChatMessage message, String photoUrl) {
        return ChatMessageResponse.builder()
                .chatMessageId(message.getId())
                .senderType(message.getSenderType())
                .content(message.getContent())
                .messageType(message.getMessageType())
                .photoUrl(photoUrl)
                .createdAt(message.getCreatedAt())
                .build();
    }
}
