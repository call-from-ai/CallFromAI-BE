package com.example.umcCall.domain.chat.service;

import com.example.umcCall.domain.chat.dto.response.ChatMessageResponse;
import com.example.umcCall.domain.chat.entity.ChatMessage;
import com.example.umcCall.domain.chat.entity.ChatPhoto;
import com.example.umcCall.domain.chat.entity.ChatRoom;
import com.example.umcCall.domain.chat.enums.MessageType;
import com.example.umcCall.domain.chat.enums.SenderType;
import com.example.umcCall.domain.chat.event.UserMessageSentEvent;
import com.example.umcCall.domain.chat.repository.ChatMessageRepository;
import com.example.umcCall.domain.chat.repository.ChatPhotoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 유저 메시지의 DB 저장만 담당하는 컴포넌트.
 * S3 업로드 같은 느린 외부 I/O는 호출부(ChatMessageService)가 트랜잭션 밖에서 끝낸 뒤,
 * 원자성이 필요한 DB 쓰기(메시지+사진+마지막시각+이벤트)만 여기서 한 트랜잭션으로 묶는다.
 */
@Component
@RequiredArgsConstructor
public class ChatMessageWriter {

    private final ChatRoomFinder chatRoomFinder;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatPhotoRepository chatPhotoRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 유저 메시지(+사진 URL)를 저장한다. photoUrl이 있으면 사진 메시지로 취급한다.
     * 방은 이 트랜잭션 안에서 다시 조회해 managed 상태로 만든다(updateLastMessageAt 변경 감지용).
     */
    @Transactional
    public ChatMessageResponse saveUserMessage(Long chatRoomId, Long memberId, String content, String photoUrl) {
        ChatRoom room = chatRoomFinder.getOwnedRoom(chatRoomId, memberId);

        boolean hasText = content != null && !content.isBlank();
        boolean hasImage = photoUrl != null;

        ChatMessage message = chatMessageRepository.save(
                ChatMessage.builder()
                        .senderType(SenderType.USER)
                        .content(hasText ? content : null)
                        .messageType(resolveMessageType(hasText, hasImage))
                        .read(true)      // 내가 보낸 메시지는 읽음 처리
                        .chatRoom(room)
                        .build()
        );

        if (hasImage) {
            chatPhotoRepository.save(
                    ChatPhoto.builder().chatMessage(message).photoUrl(photoUrl).build());
        }

        room.updateLastMessageAt(message.getCreatedAt());

        // 커밋 후 AI 답장 생성을 트리거한다(캐릭터방만). AFTER_COMMIT 리스너가 받으므로 트랜잭션 안에서 발행한다.
        if (room.getRelationshipId() != null) {
            eventPublisher.publishEvent(new UserMessageSentEvent(chatRoomId));
        }

        return ChatMessageResponse.from(message, photoUrl);
    }

    /** content·image 조합으로 메시지 타입을 계산한다. */
    private MessageType resolveMessageType(boolean hasText, boolean hasImage) {
        if (hasText && hasImage) {
            return MessageType.TEXT_IMAGE;
        }
        return hasImage ? MessageType.IMAGE : MessageType.TEXT;
    }
}
