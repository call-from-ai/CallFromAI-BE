package com.example.umcCall.domain.chat.repository;

import com.example.umcCall.domain.chat.entity.ChatPhoto;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatPhotoRepository extends JpaRepository<ChatPhoto, Long> {

    /**
     * 여러 메시지의 사진을 한 번에 조회한다(목록/조회에서 photoUrl 배치 채우기용).
     * chatMessageId → photoUrl 매핑을 만들 때 쓴다.
     */
    @Query("""
            select p from ChatPhoto p
            where p.chatMessage.id in :messageIds
            """)
    List<ChatPhoto> findByMessageIds(@Param("messageIds") Collection<Long> messageIds);
}
