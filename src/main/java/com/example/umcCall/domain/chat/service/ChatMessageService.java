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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatMessageService {

    private static final int DEFAULT_SIZE = 30;
    private static final int MAX_SIZE = 50;

    private final ChatRoomFinder chatRoomFinder;
    private final ChatMessageRepository chatMessageRepository;

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

        // 과거순으로 뒤집어 응답 -> 위에서 아래, 순차적으로 전송하기 위해
        Collections.reverse(page);
        List<ChatMessageResponse> content = page.stream()
                .map(ChatMessageResponse::from)
                .toList();

        return ChatMessageCursorResponse.of(content, nextCursor, hasNext);
    }

    /**
     * 채팅 메시지 전송(텍스트).
     * 방 소유 검증 후 유저 메시지를 저장하고, 저장된 메시지만 반환한다.
     * AI 답장은 이 응답에 포함되지 않고 이후 SSE로 별도 전달한다.
     * (사진 전송은 S3 연동 후 image 파라미터로 확장 예정)
     */
    @Transactional
    public ChatMessageResponse sendMessage(Long memberId, Long chatRoomId, String content) {
        ChatRoom room = chatRoomFinder.getOwnedRoom(chatRoomId, memberId);

        // 내용이 없으면 전송 불가 (이미지 붙으면 "content 또는 image 최소 하나"로 완화)
        if (content == null || content.isBlank()) {
            throw new ChatException(ChatErrorCode.EMPTY_MESSAGE);
        }

        ChatMessage message = chatMessageRepository.save(
                ChatMessage.builder()
                        .senderType(SenderType.USER)
                        .content(content)
                        .messageType(MessageType.TEXT)
                        .read(true)      // 내가 보낸 메시지는 읽음 처리
                        .deleted(false)
                        .chatRoom(room)
                        .build()
        );

        // 목록 정렬용 마지막 메시지 시각 갱신
        room.updateLastMessageAt(message.getCreatedAt());

        return ChatMessageResponse.from(message);
    }

    /** size 미지정/비정상은 기본값, 상한 초과는 상한으로 */
    private int normalizeSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}
