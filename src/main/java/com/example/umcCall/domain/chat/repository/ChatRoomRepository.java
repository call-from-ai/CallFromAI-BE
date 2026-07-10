package com.example.umcCall.domain.chat.repository;

import com.example.umcCall.domain.chat.entity.ChatRoom;
import com.example.umcCall.domain.chat.entity.RoomType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    /**
     * 목록 노출 대상 방 조회.
     */
    List<ChatRoom> findByMemberIdAndRoomTypeAndDeletedFalseOrderByLastMessageAtDesc(
            Long memberId, RoomType roomType);
}
