package com.example.umcCall.domain.chat.service;

import com.example.umcCall.domain.chat.entity.ChatRoom;
import com.example.umcCall.domain.chat.exception.ChatErrorCode;
import com.example.umcCall.domain.chat.exception.ChatException;
import com.example.umcCall.domain.chat.repository.ChatRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * "이 방이 내 방이 맞는지" 검증하고 방을 돌려주는 공용 컴포넌트.
 * 메시지 조회/전송/삭제, 방 음소거/숨김 등 방을 다루는 여러 기능에 재사용
 */
@Component
@RequiredArgsConstructor
public class ChatRoomFinder {

    private final ChatRoomRepository chatRoomRepository;

    /**
     * 방을 찾아 소유·상태를 검증한 뒤 반환한다.
     * - 존재하지 않으면 ROOM_NOT_FOUND
     * - 내 방이 아니면 ROOM_ACCESS_DENIED
     * - 목록에서 숨긴 방이면 ROOM_HIDDEN
     */
    public ChatRoom getOwnedRoom(Long chatRoomId, Long memberId) {
        ChatRoom room = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new ChatException(ChatErrorCode.CHATROOM_NOT_FOUND));

        if (!room.getMemberId().equals(memberId)) {
            throw new ChatException(ChatErrorCode.CHATROOM_ACCESS_DENIED);
        }
        if (room.isDeleted()) {
            throw new ChatException(ChatErrorCode.CHATROOM_HIDDEN);
        }
        return room;
    }
}
