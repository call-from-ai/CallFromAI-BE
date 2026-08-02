package com.example.umcCall.domain.call.repository;

import com.example.umcCall.domain.call.entity.CallHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 통화 전사(transcript) 저장/조회 담당.
 */
public interface CallHistoryRepository extends JpaRepository<CallHistory, Long> {

    /** 통화의 전사 전체를 발화 순서(id ASC = 과거→최신)로 조회한다. */
    List<CallHistory> findByCallIdOrderByIdAsc(Long callId);

    /**
     * 관계에 속한 모든 통화의 전사를 지운다. 캐릭터 물리 삭제(하드 딜리트)에서 <b>통화보다 먼저</b> 호출된다.
     * <p>⚠ {@code CallHistory.call}은 {@code nullable=false} FK인데 {@code Call}엔 cascade가 없다 —
     * 전사를 남겨둔 채 통화를 지우면 FK 제약으로 터진다.
     * <p>벌크 삭제라 영속성 컨텍스트를 거치지 않는다(같은 tx에서 전사 엔티티를 다시 읽지 말 것).
     */
    @Modifying
    @Query("delete from CallHistory h where h.call.id in "
            + "(select c.id from Call c where c.relationship.id = :relationshipId)")
    void deleteByRelationshipId(@Param("relationshipId") Long relationshipId);
}
