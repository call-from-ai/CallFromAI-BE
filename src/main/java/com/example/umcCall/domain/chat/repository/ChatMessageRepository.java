package com.example.umcCall.domain.chat.repository;

import com.example.umcCall.domain.chat.entity.ChatMessage;
import com.example.umcCall.domain.chat.enums.SenderType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    void deleteByChatRoomId(Long chatRoomId);

    /** 방에 속한 특정 메시지를 조회한다(개별 삭제 시 소유·소속 검증용). */
    @Query("select m from ChatMessage m where m.id = :messageId and m.chatRoom.id = :roomId")
    Optional<ChatMessage> findByIdAndChatRoomId(@Param("messageId") Long messageId, @Param("roomId") Long roomId);

    boolean existsByProactiveRequestId(String proactiveRequestId);

    Optional<ChatMessage> findTopByChatRoomIdAndDeletedFalseOrderByIdDesc(Long chatRoomId);

    Optional<ChatMessage> findTopByChatRoomIdAndSenderTypeAndDeletedFalseOrderByIdDesc(
            Long chatRoomId, SenderType senderType);

    @Query("""
            select m from ChatMessage m
            where m.chatRoom.id = :roomId and m.deleted = false
            order by m.id desc
            """)
    List<ChatMessage> findRecent(@Param("roomId") Long roomId, Pageable pageable);

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

    /**
     * 커서 기반 메시지 조회.
     * 특정 방의 미삭제 + cutoff 초과 메시지를 cursor보다 과거(id < cursor)로 최신순(DESC) 조회
     * 개수 제한은 Pageable로 준다(page 0, size = 요청 size + 1로 hasNext 판단)
     */
    @Query("""
            select m from ChatMessage m
            where m.chatRoom.id = :roomId
              and m.deleted = false
              and (:cutoff is null or m.id > :cutoff)
              and (:cursor is null or m.id < :cursor)
            order by m.id desc
            """)
    List<ChatMessage> findMessagesByCursor(@Param("roomId") Long roomId,
                                           @Param("cutoff") Long cutoff,
                                           @Param("cursor") Long cursor,
                                           Pageable pageable);

    /**
     * 방의 안 읽은 수신 메시지를 일괄 읽음 처리.
     * 기준은 안 읽음 집계와 동일: 수신 메시지 + 미읽음 + 미삭제 + cutoff 초과.
     * @return 읽음 처리된 메시지 수
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            update ChatMessage m
            set m.read = true
            where m.chatRoom.id = :roomId
              and m.read = false
              and m.deleted = false
              and m.senderType <> :excludedSender
              and (:cutoff is null or m.id > :cutoff)
            """)
    int markIncomingAsRead(@Param("roomId") Long roomId,
                           @Param("cutoff") Long cutoff,
                           @Param("excludedSender") SenderType excludedSender);

    /**
     * 방의 마지막 AI 메시지 id를 조회
     * ai가 "이미 답한 경계"의 근사값으로 쓴다.
     */
    @Query("select max(m.id) from ChatMessage m where m.chatRoom.id = :roomId and m.senderType = :aiSender")
    Long findMaxAiMessageId(@Param("roomId") Long roomId, @Param("aiSender") SenderType aiSender);

    /**
     * afterId 초과의 유저 메시지를 오래된 순(id ASC)으로 조회한다.
     * 디바운스 배치 = "아직 AI가 답하지 않은 유저 메시지들"
     */
    @Query("""
            select m from ChatMessage m
            where m.chatRoom.id = :roomId
              and m.senderType = :userSender
              and m.deleted = false
              and m.id > :afterId
            order by m.id asc
            """)
    List<ChatMessage> findUserMessagesAfter(@Param("roomId") Long roomId,
                                            @Param("userSender") SenderType userSender,
                                            @Param("afterId") Long afterId,
                                            Pageable pageable);

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
