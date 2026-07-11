package com.example.umcCall.domain.chat.repository;

import com.example.umcCall.domain.chat.entity.ChatMessage;
import com.example.umcCall.domain.chat.enums.SenderType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /**
     * 방별 안 읽음 수 일괄 집계
     * 기준: 수신 메시지 + 미읽음 + 미삭제 + 이 방의 message_visible_after_id 초과
     */
    @Query("""
            select m.chatRoom.id as roomId, count(m) as unreadCount
            from ChatMessage m
            where m.chatRoom.id in :roomIds
              and m.read = false
              and m.deleted = false
              and m.senderType <> :excludedSender
              and (m.chatRoom.messageVisibleAfterId is null
                   or m.id > m.chatRoom.messageVisibleAfterId)
            group by m.chatRoom.id
            """)
    List<UnreadCountRow> countUnreadByRoomIds(@Param("roomIds") Collection<Long> roomIds,
                                              @Param("excludedSender") SenderType excludedSender);

    /**
     * 방별 "보이는 마지막 메시지 id" 일괄 조회
     */
    @Query("""
            select m.chatRoom.id as roomId, max(m.id) as lastMessageId
            from ChatMessage m
            where m.chatRoom.id in :roomIds
              and m.deleted = false
              and (m.chatRoom.messageVisibleAfterId is null
                   or m.id > m.chatRoom.messageVisibleAfterId)
            group by m.chatRoom.id
            """)
    List<LastMessageIdRow> findLastMessageIdByRoomIds(@Param("roomIds") Collection<Long> roomIds);

    /** 방별 안 읽음 수 집계 결과 프로젝션. */
    interface UnreadCountRow {
        Long getRoomId();

        long getUnreadCount();
    }

    /** 방별 마지막 메시지 id 프로젝션. */
    interface LastMessageIdRow {
        Long getRoomId();

        Long getLastMessageId();
    }
}
