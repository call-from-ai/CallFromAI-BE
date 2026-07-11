package com.example.umcCall.domain.chat.repository;

import com.example.umcCall.domain.chat.entity.ChatRoom;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 채팅방 DB 조회/저장 담당.
 */
public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    // 캐릭터 목록 조회에서 "마지막 대화" 표시용 (캐릭터 생성과는 무관)
    Optional<ChatRoom> findByRelationshipId(Long relationshipId);
}
