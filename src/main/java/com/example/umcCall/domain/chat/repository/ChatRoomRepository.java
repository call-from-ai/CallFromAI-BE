package com.example.umcCall.domain.chat.repository;

import com.example.umcCall.domain.chat.entity.ChatRoom;
import com.example.umcCall.domain.chat.enums.RoomType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    // 캐릭터 목록 조회에서 "마지막 대화" 표시용 (캐릭터 관리에서 표시되는 마지막 대화 시각)
    Optional<ChatRoom> findByRelationshipId(Long relationshipId);

    /**
     * 채팅방 목록 대상 방 조회.
     */
    List<ChatRoom> findByMemberIdAndRoomTypeAndDeletedFalseOrderByLastMessageAtDesc(
            Long memberId, RoomType roomType);

    // 캐릭터 삭제 시 연결된 채팅방도 함께 삭제
    void deleteByRelationshipId(Long relationshipId);
}
