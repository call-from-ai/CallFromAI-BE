package com.example.umcCall.domain.chat.dto.response;

import com.example.umcCall.domain.chat.entity.ChatMessage;
import com.example.umcCall.domain.chat.enums.MessageType;
import com.example.umcCall.domain.chat.enums.SenderType;
import java.time.LocalDateTime;
import lombok.Builder;

/**
 * 채팅 메시지 1건 응답.
 * photoUrl은 사진 전송 구현 전까지 null
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
    public static ChatMessageResponse from(ChatMessage message) {
        return ChatMessageResponse.builder()
                .chatMessageId(message.getId())
                .senderType(message.getSenderType())
                .content(message.getContent())
                .messageType(message.getMessageType())
                .photoUrl(null) // TODO: 사진 전송 구현 시 chat_photo 조인해 채움
                .createdAt(message.getCreatedAt())
                .build();
    }
}
