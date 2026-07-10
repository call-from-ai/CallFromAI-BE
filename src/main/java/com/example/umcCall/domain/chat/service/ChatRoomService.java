package com.example.umcCall.domain.chat.service;

import com.example.umcCall.domain.chat.dto.response.ChatRoomSummaryResponse;
import com.example.umcCall.domain.chat.entity.ChatMessage;
import com.example.umcCall.domain.chat.entity.ChatRoom;
import com.example.umcCall.domain.chat.entity.MessageType;
import com.example.umcCall.domain.chat.entity.RoomType;
import com.example.umcCall.domain.chat.port.CharacterSummary;
import com.example.umcCall.domain.chat.port.CharacterSummaryProvider;
import com.example.umcCall.domain.chat.repository.ChatMessageRepository;
import com.example.umcCall.domain.chat.repository.ChatMessageRepository.LastMessageIdRow;
import com.example.umcCall.domain.chat.repository.ChatMessageRepository.UnreadCountRow;
import com.example.umcCall.domain.chat.repository.ChatRoomRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatRoomService {

    private static final String PHOTO_PREVIEW = "[사진]";

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final CharacterSummaryProvider characterSummaryProvider;

    /**
     * 채팅방 목록 조회.
     * 방 목록을 한 번 조회한 뒤, 안읽음 수 / 마지막 메시지 / 캐릭터 정보를
     * 각각 IN 절 배치 조회로 모아 조립
     */
    public List<ChatRoomSummaryResponse> getChatRooms(Long memberId) {
        // 1. 노출 대상 방 (CHARACTER, 미삭제, last_message_at DESC)
        List<ChatRoom> rooms = chatRoomRepository
                .findByMemberIdAndRoomTypeAndDeletedFalseOrderByLastMessageAtDesc(memberId, RoomType.CHARACTER);
        if (rooms.isEmpty()) {
            return List.of();
        }
        List<Long> roomIds = rooms.stream().map(ChatRoom::getId).toList();

        // 2. 방별 안읽음 수 배치 집계
        Map<Long, Long> unreadByRoom = chatMessageRepository.countUnreadByRoomIds(roomIds).stream()
                .collect(Collectors.toMap(UnreadCountRow::getRoomId, UnreadCountRow::getUnreadCount));

        // 3. 방별 마지막 메시지 조회 (id → 본문)
        Map<Long, Long> lastIdByRoom = chatMessageRepository.findLastMessageIdByRoomIds(roomIds).stream()
                .collect(Collectors.toMap(LastMessageIdRow::getRoomId, LastMessageIdRow::getLastMessageId));
        Map<Long, ChatMessage> messageById = chatMessageRepository.findAllById(lastIdByRoom.values()).stream()
                .collect(Collectors.toMap(ChatMessage::getId, Function.identity()));

        // 4. 캐릭터 정보 배치 조회 (포트 — 지금은 더미)
        List<Long> relationshipIds = rooms.stream()
                .map(ChatRoom::getRelationshipId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, CharacterSummary> summaryByRelationship = characterSummaryProvider.getSummaries(relationshipIds);

        // 5. 조립
        List<ChatRoomSummaryResponse> result = rooms.stream()
                .map(room -> {
                    ChatMessage lastMessage = messageById.get(lastIdByRoom.get(room.getId()));
                    CharacterSummary summary = room.getRelationshipId() == null
                            ? null
                            : summaryByRelationship.get(room.getRelationshipId());
                    return toResponse(room, unreadByRoom.getOrDefault(room.getId(), 0L), lastMessage, summary);
                })
                .collect(Collectors.toCollection(java.util.ArrayList::new));

        // 6. is_main 최상단 고정
        result.sort(Comparator.comparing(ChatRoomSummaryResponse::isMain).reversed());
        return result;
    }

    private ChatRoomSummaryResponse toResponse(ChatRoom room, long unreadCount,
                                               ChatMessage lastMessage, CharacterSummary summary) {
        return ChatRoomSummaryResponse.builder()
                .chatRoomId(room.getId())
                .characterName(summary != null ? summary.characterName() : null)
                .characterProfileUrl(summary != null ? summary.profileUrl() : null)
                .isMain(summary != null && summary.isMain())
                .isMuted(room.isMuted())
                .lastMessage(buildPreview(lastMessage))
                .lastMessageAt(lastMessage != null ? lastMessage.getCreatedAt() : null)
                .unreadCount(unreadCount)
                .build();
    }

    /**
     * 미리보기 문자열
     */
    private String buildPreview(ChatMessage message) {
        /*채팅방에 존재하는 모든 메시지를 유저가 삭제 하거나 캐릭터가 아직 메시지를 보내지 않았을때
        null을 리턴하도록 했으나 백에서 정하맂 vs 프론트에서 처리를 할 지에 따라 수정하겠음.
         */
        if (message == null) {
            return null;
        }
        if (message.getMessageType() == MessageType.IMAGE) {
            return PHOTO_PREVIEW;
        }
        String content = message.getContent();
        return (content == null || content.isBlank()) ? PHOTO_PREVIEW : content;
    }
}
