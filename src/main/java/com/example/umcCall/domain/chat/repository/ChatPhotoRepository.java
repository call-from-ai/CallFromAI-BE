package com.example.umcCall.domain.chat.repository;

import com.example.umcCall.domain.chat.entity.ChatPhoto;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatPhotoRepository extends JpaRepository<ChatPhoto, Long> {

    /**
     * 여러 메시지의 사진 URL을 한 번에 조회한다(조회에서 photoUrl 배치 채우기용).
     * chatMessage 엔티티를 로딩하지 않도록 messageId·photoUrl만 프로젝션으로 뽑는다.
     */
    @Query("""
            select p.chatMessage.id as messageId, p.photoUrl as photoUrl
            from ChatPhoto p
            where p.chatMessage.id in :messageIds
            """)
    List<MessagePhotoRow> findPhotoUrlsByMessageIds(@Param("messageIds") Collection<Long> messageIds);

    /** 메시지 id → 사진 URL 프로젝션. */
    interface MessagePhotoRow {
        Long getMessageId();

        String getPhotoUrl();
    }
}
