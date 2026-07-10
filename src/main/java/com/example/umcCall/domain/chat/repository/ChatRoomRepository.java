package com.example.umcCall.domain.chat.repository;

import com.example.umcCall.domain.chat.entity.ChatRoom;
import com.example.umcCall.domain.chat.enums.RoomType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    /**
     * 채팅방 목록 대상 방 조회.
     */
    List<ChatRoom> findByMemberIdAndRoomTypeAndDeletedFalseOrderByLastMessageAtDesc(
            Long memberId, RoomType roomType);
}
