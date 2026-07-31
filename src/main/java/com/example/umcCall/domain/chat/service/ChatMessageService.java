package com.example.umcCall.domain.chat.service;

import com.example.umcCall.domain.chat.dto.response.ChatMessageCursorResponse;
import com.example.umcCall.domain.chat.dto.response.ChatMessageResponse;
import com.example.umcCall.domain.chat.entity.ChatMessage;
import com.example.umcCall.domain.chat.entity.ChatRoom;
import com.example.umcCall.domain.chat.enums.MessageType;
import com.example.umcCall.domain.chat.enums.SenderType;
import com.example.umcCall.domain.chat.exception.ChatErrorCode;
import com.example.umcCall.domain.chat.exception.ChatException;
import com.example.umcCall.domain.chat.repository.ChatMessageRepository;
import com.example.umcCall.domain.chat.repository.ChatPhotoRepository;
import com.example.umcCall.domain.chat.repository.ChatRoomRepository;
import com.example.umcCall.domain.relationship.repository.ChatSummaryRepository;
import com.example.umcCall.global.infra.s3.S3Uploader;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
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
    private final ChatPhotoCleaner chatPhotoCleaner;
    private final ChatMessageWriter chatMessageWriter;
    private final ChatSummaryRepository chatSummaryRepository;
    private final S3Uploader s3Uploader;

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
     * content·image 중 최소 하나가 있어야 하며, message_type은 조합으로 서버가 계산한다.
     *  S3 업로드는 트랜잭션 밖에서 하고,
     * 원자성이 필요한 DB 쓰기만 ChatMessageWriter의 트랜잭션에 맡긴다(업로드 동안 DB 커넥션을 쥐지 않도록).
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ChatMessageResponse sendMessage(Long memberId, Long chatRoomId, String content, MultipartFile image) {
        // 업로드 전에 방 소유부터 검증한다
        chatRoomFinder.getOwnedRoom(chatRoomId, memberId);

        boolean hasText = content != null && !content.isBlank();
        boolean hasImage = image != null && !image.isEmpty();
        if (!hasText && !hasImage) {
            throw new ChatException(ChatErrorCode.EMPTY_MESSAGE);
        }

        // 이미지가 있으면 형식 검증 후 S3에 올린다
        String photoUrl = null;
        if (hasImage) {
            validateImageType(image);
            photoUrl = s3Uploader.upload(image, CHAT_PHOTO_DIR + "/" + chatRoomId);
        }

        // 원자적 DB 쓰기(메시지+사진+시각+이벤트)는 별도 빈의 트랜잭션에서 처리한다.
        // DB 저장이 실패하면 방금 올린 S3 객체가 고아로 남으므로, 보상 삭제 후 예외를 다시 던진다.
        try {
            return chatMessageWriter.saveUserMessage(chatRoomId, memberId, content, photoUrl);
        } catch (RuntimeException e) {
            if (photoUrl != null) {
                deleteQuietly(photoUrl);   // 업로드했던 사진 되돌리기(삭제 실패는 삼켜 원래 예외를 우선 전달)
            }
            throw e;
        }
    }

    /** 보상 삭제. 삭제가 실패해도 원래 예외를 덮지 않도록 로깅만 하고 넘어간다. */
    private void deleteQuietly(String photoUrl) {
        try {
            s3Uploader.delete(photoUrl);
        } catch (Exception e) {
            log.warn("업로드 롤백용 S3 삭제 실패(고아 객체가 남을 수 있음). url={}", photoUrl, e);
        }
    }

    /**
     * 이미지가 진짜 JPEG/PNG인지 검증한다.
     * content-type 헤더는 클라이언트가 위조할 수 있으므로, 파일 앞부분의 매직 바이트(실제 내용)까지 확인한다.
     */
    private void validateImageType(MultipartFile image) {
        String contentType = image.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType)) {
            throw new ChatException(ChatErrorCode.INVALID_IMAGE_TYPE);
        }
        if (!hasImageMagicBytes(image)) {
            throw new ChatException(ChatErrorCode.INVALID_IMAGE_TYPE);
        }
    }

    /** 파일 앞부분을 읽어 JPEG(FF D8 FF) 또는 PNG(89 50 4E 47) 시그니처인지 확인한다. */
    private boolean hasImageMagicBytes(MultipartFile image) {
        byte[] head = new byte[4];
        try (InputStream in = image.getInputStream()) {
            if (in.readNBytes(head, 0, 4) < 4) {
                return false;   // 4바이트도 안 되면 정상 이미지가 아님
            }
        } catch (IOException e) {
            throw new ChatException(ChatErrorCode.INVALID_IMAGE_TYPE);
        }

        boolean jpeg = (head[0] & 0xFF) == 0xFF
                && (head[1] & 0xFF) == 0xD8
                && (head[2] & 0xFF) == 0xFF;
        boolean png = (head[0] & 0xFF) == 0x89
                && (head[1] & 0xFF) == 0x50
                && (head[2] & 0xFF) == 0x4E
                && (head[3] & 0xFF) == 0x47;
        return jpeg || png;
    }

    /**
     * 개별 메시지 삭제(물리 삭제). 유저·AI 메시지 모두 삭제 가능하며, TEXT_IMAGE는 텍스트+사진이 통째로 지워진다.
     * 사진이 있으면 chat_photo를 먼저(FK) 지우고 커밋 후 S3 객체까지 삭제한다.
     * 마지막 메시지를 지운 경우 목록 정렬이 어긋나지 않도록 last_message_at을 남은 최신 메시지 시각으로 갱신한다.
     */
    @Transactional
    public void deleteMessage(Long memberId, Long chatRoomId, Long messageId) {
        ChatRoom room = chatRoomFinder.getOwnedRoom(chatRoomId, memberId);
        ChatMessage message = chatMessageRepository.findByIdAndChatRoomId(messageId, chatRoomId)
                .orElseThrow(() -> new ChatException(ChatErrorCode.MESSAGE_NOT_FOUND));

        // 사진(자식)을 먼저 정리: chat_photo 삭제 + 커밋 후 S3 삭제. 그래야 메시지 물리 삭제 시 FK가 안 걸린다.
        chatPhotoCleaner.purgeMessagePhoto(messageId);
        chatMessageRepository.delete(message);
<<<<<<< HEAD
        chatSummaryRepository.deleteByRelationshipId(room.getRelationshipId());

=======
>>>>>>> 04b019de2bb4187faa9995c330ab11291803b3a2
        chatSummaryRepository.deleteByRelationshipId(room.getRelationshipId());

        // 남은 최신 메시지 기준으로 마지막 메시지 시각 재계산(없으면 null).
        LocalDateTime lastMessageAt = chatMessageRepository
                .findTopByChatRoomIdAndDeletedFalseOrderByIdDesc(chatRoomId)
                .map(ChatMessage::getCreatedAt)
                .orElse(null);
        room.updateLastMessageAt(lastMessageAt);
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
        room.reveal();   // 숨긴 방이었으면 AI 답장으로 목록에 다시 노출한다.
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
