package com.example.umcCall.domain.call.repository;

import com.example.umcCall.domain.call.dto.response.CallIncomingResponse;
import com.example.umcCall.domain.call.dto.response.CallListItem;
import com.example.umcCall.domain.call.entity.Call;
import com.example.umcCall.domain.call.enums.CallStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import com.example.umcCall.domain.call.enums.CallSender;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 통화 기록 저장/조회 담당. 전사(transcript) 저장·통화 이력 조회가 여기에 붙는다.
 */
public interface CallRepository extends JpaRepository<Call, Long> {

    /**
     * 회원의 통화 목록을 최신순(createdAt DESC)으로 조회한다. 상대 캐릭터 이름까지 한 번에 조인해
     * DTO로 프로젝션한다(N+1·lazy 회피). 개수 제한·상태 필터는 호출부가 준다.
     */
    @Query("""
            select new com.example.umcCall.domain.call.dto.response.CallListItem(
                c.id, ch.firstName, c.sender, c.aiSummary, c.createdAt, c.status)
            from Call c
                join c.relationship r
                join r.character ch
            where r.memberId = :memberId
              and c.status in :statuses
            order by c.createdAt desc
            """)
    List<CallListItem> findRecentCallList(@Param("memberId") Long memberId,
                                          @Param("statuses") Collection<CallStatus> statuses,
                                          Pageable pageable);

    /**
     * 회원의 착신 대기(RINGING) 통화를 최신순으로 조회한다. 상대 캐릭터까지 조인해 DTO로 프로젝션한다.
     * <p>착신은 하나뿐이라 호출부가 1건만 요청하지만, 반환형은 {@code List}로 둔다 — 회원 기준
     * RINGING이 2건 생기는 엣지(메인 캐릭터 교체)에서 {@code Optional} 반환은 예외로 터진다.
     * <p>상태를 파라미터로 받아 "무엇이 착신인가"는 호출부(서비스)가 정한다.
     */
    @Query("""
            select new com.example.umcCall.domain.call.dto.response.CallIncomingResponse(
                c.id, ch.id, ch.firstName, ch.imageUrl, c.createdAt)
            from Call c
                join c.relationship r
                join r.character ch
            where r.memberId = :memberId
              and c.status = :status
            order by c.createdAt desc
            """)
    List<CallIncomingResponse> findIncomingCalls(@Param("memberId") Long memberId,
                                                 @Param("status") CallStatus status,
                                                 Pageable pageable);

    boolean existsByRelationshipIdAndStatusIn(Long relationshipId, Collection<CallStatus> statuses);

    long countByRelationshipIdAndSenderAndStatusInAndCreatedAtAfter(
            Long relationshipId,
            CallSender sender,
            Collection<CallStatus> statuses,
            LocalDateTime createdAt);
}
