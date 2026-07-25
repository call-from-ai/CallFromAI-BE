package com.example.umcCall.domain.chat.service;

import com.example.umcCall.domain.chat.dto.response.ChatMessageCursorResponse;
import com.example.umcCall.domain.chat.dto.response.ChatMessageResponse;
import com.example.umcCall.domain.chat.entity.ChatMessage;
import com.example.umcCall.domain.chat.entity.ChatPhoto;
import com.example.umcCall.domain.chat.entity.ChatRoom;
import com.example.umcCall.domain.chat.enums.MessageType;
import com.example.umcCall.domain.chat.enums.SenderType;
import com.example.umcCall.domain.chat.event.UserMessageSentEvent;
import com.example.umcCall.domain.chat.exception.ChatErrorCode;
import com.example.umcCall.domain.chat.exception.ChatException;
import com.example.umcCall.domain.chat.repository.ChatMessageRepository;
import com.example.umcCall.domain.chat.repository.ChatPhotoRepository;
import com.example.umcCall.domain.chat.repository.ChatRoomRepository;
import com.example.umcCall.global.infra.s3.S3Uploader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatMessageService {

    private static final int DEFAULT_SIZE = 30;
    private static final int MAX_SIZE = 50;

    /** 허용 이미지 형식. AI 서버가 받는 것과 통일한다. */
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of("image/jpeg", "image/png");
    /** S3 내 채팅 사진 접두어. 프리셋 등 다른 객체와 섞이지 않게 분리한다. */
    private static final String CHAT_PHOTO_DIR = "chat-photos";

    private final ChatRoomFinder chatRoomFinder;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatPhotoRepository chatPhotoRepository;
    private final S3Uploader s3Uploader;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 채팅방 메시지 커서 조회.
     * cutoff(message_visible_after_id) 초과 + 미삭제 메시지를, cursor보다 과거로 size개씩 준다.
     * size+1개를 조회해 hasNext를 판단하고, 응답은 과거순으로 뒤집어 반환한다.
     */
    public ChatMessageCursorResponse getMessages(Long memberId, Long chatRoomId, Long cursor, Integer size) {
        ChatRoom room = chatRoomFinder.getOwnedRoom(chatRoomId, memberId);
        int pageSize = normalizeSize(size);

        // 최신 size+1개 조회 -> 초과분 유무로 hasNext 판단
        List<ChatMessage> rows = chatMessageRepository.findMessagesByCursor(
                room.getId(), room.getMessageVisibleAfterId(), cursor, PageRequest.of(0, pageSize + 1));

        boolean hasNext = rows.size() > pageSize;
        List<ChatMessage> page = new ArrayList<>(hasNext ? rows.subList(0, pageSize) : rows);

        // 다음 커서 = 이번 페이지에서 가장 오래된(마지막) 메시지 id (더 없으면 null)
        Long nextCursor = hasNext ? page.get(page.size() - 1).getId() : null;

        // 이 페이지 메시지들의 사진 URL을 한 번에 조회해 map으로 준비(없으면 빈 map)
        Map<Long, String> photoUrlByMessageId = loadPhotoUrls(page);

        // 과거순으로 뒤집어 응답 -> 위에서 아래, 순차적으로 전송하기 위해
        Collections.reverse(page);
        List<ChatMessageResponse> content = page.stream()
                .map(m -> ChatMessageResponse.from(m, photoUrlByMessageId.get(m.getId())))
                .toList();

        return ChatMessageCursorResponse.of(content, nextCursor, hasNext);
    }

    /**
     * 채팅 메시지 전송(텍스트/사진).
     * 방 소유 검증 후 유저 메시지를 저장하고, 저장된 메시지만 반환한다.
     * content·image 중 최소 하나가 있어야 하며, message_type은 조합으로 서버가 계산한다.
     * 이미지는 S3에 먼저 올린 뒤 DB를 저장해, S3 실패 시 깨진 메시지가 남지 않게 한다.
     * AI 답장은 이 응답에 포함되지 않고 이후 SSE로 별도 전달한다.
     */
    @Transactional
    public ChatMessageResponse sendMessage(Long memberId, Long chatRoomId, String content, MultipartFile image) {
        ChatRoom room = chatRoomFinder.getOwnedRoom(chatRoomId, memberId);

        boolean hasText = content != null && !content.isBlank();
        boolean hasImage = image != null && !image.isEmpty();
        if (!hasText && !hasImage) {
            throw new ChatException(ChatErrorCode.EMPTY_MESSAGE);
        }

        // 이미지가 있으면 형식 검증 후 S3에 먼저 업로드한다(DB 저장 전).
        String photoUrl = null;
        if (hasImage) {
            validateImageType(image);
            photoUrl = s3Uploader.upload(image, CHAT_PHOTO_DIR + "/" + room.getId());
        }

        ChatMessage message = chatMessageRepository.save(
                ChatMessage.builder()
                        .senderType(SenderType.USER)
                        .content(hasText ? content : null)
                        .messageType(resolveMessageType(hasText, hasImage))
                        .read(true)      // 내가 보낸 메시지는 읽음 처리
                        .deleted(false)
                        .chatRoom(room)
                        .build()
        );

        // 사진은 별도 테이블에 1:1로 저장한다.
        if (hasImage) {
            chatPhotoRepository.save(
                    ChatPhoto.builder().chatMessage(message).photoUrl(photoUrl).build());
        }

        // 목록 정렬용 마지막 메시지 시각 갱신
        room.updateLastMessageAt(message.getCreatedAt());

        // 커밋 후 AI 답장 생성을 트리거한다(캐릭터방만). 전송 응답은 기다리지 않는다.
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

    /** 이미지 content-type이 허용 목록(JPEG/PNG)에 있는지 검증한다. */
    private void validateImageType(MultipartFile image) {
        String contentType = image.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType)) {
            throw new ChatException(ChatErrorCode.INVALID_IMAGE_TYPE);
        }
    }

    /**
     * AI 답장 메시지를 저장하고 방의 마지막 메시지 시각을 갱신한다(AI 답장 처리에서 호출).
     */
    @Transactional
    public ChatMessage saveAiMessage(Long chatRoomId, String content) {
        ChatRoom room = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new ChatException(ChatErrorCode.CHATROOM_NOT_FOUND));
        ChatMessage message = chatMessageRepository.save(
                ChatMessage.builder()
                        .senderType(SenderType.AI)
                        .content(content)
                        .messageType(MessageType.TEXT)
                        .read(false)     // 유저가 아직 안 읽음 → 안읽음 집계 대상
                        .deleted(false)
                        .chatRoom(room)
                        .build()
        );
        room.updateLastMessageAt(message.getCreatedAt());
        return message;
    }

    /**
     * 페이지 메시지들 중 사진이 있는 것(IMAGE/TEXT_IMAGE)의 URL을 배치 조회해 messageId→URL map으로 만든다.
     * 사진 있는 메시지가 없으면 빈 map을 반환해 불필요한 쿼리를 피한다.
     */
    private Map<Long, String> loadPhotoUrls(List<ChatMessage> messages) {
        List<Long> imageMessageIds = messages.stream()
                .filter(m -> m.getMessageType() == MessageType.IMAGE
                        || m.getMessageType() == MessageType.TEXT_IMAGE)
                .map(ChatMessage::getId)
                .toList();
        if (imageMessageIds.isEmpty()) {
            return Map.of();
        }
        return chatPhotoRepository.findPhotoUrlsByMessageIds(imageMessageIds).stream()
                .collect(Collectors.toMap(
                        ChatPhotoRepository.MessagePhotoRow::getMessageId,
                        ChatPhotoRepository.MessagePhotoRow::getPhotoUrl));
    }

    /** size 미지정/비정상은 기본값, 상한 초과는 상한으로 */
    private int normalizeSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}
