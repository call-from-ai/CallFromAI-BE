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
     * 회원의 착신 대기 통화를 최신순으로 조회한다. 캐릭터까지 조인해 DTO로 프로젝션한다(N+1 회피).
     * <p>⚠ 반환형이 {@code List}인 이유: 호출부는 1건만 쓰지만 회원 기준 RINGING이 2건 생기는
     * 엣지(메인 캐릭터 교체)가 있어 {@code Optional} 반환은 예외로 터진다.
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

    /**
     * 특정 상태로 너무 오래 머문 통화 id를 오래된 것부터 조회한다(스위퍼용).
     * <p>기준 시각은 {@code createdAt} — AI 발신은 "Call 생성 = 벨 울림"이다.
     * ⚠ FCM 푸시가 붙으면 전달 시각과 어긋날 수 있어 재검토 대상.
     */
    @Query("""
            select c.id from Call c
            where c.status = :status
              and c.createdAt < :threshold
            order by c.createdAt, c.id
            """)
    List<Long> findTimedOutIds(@Param("status") CallStatus status,
                               @Param("threshold") LocalDateTime threshold,
                               Pageable pageable);

    /**
     * 상태 전이 대상 통화를 비관적 락으로 집는다. 스위퍼 마감과 사용자 accept/connect가 겹칠 때
     * 한쪽만 이기게 한다 — 호출부는 락 뒤 상태를 다시 확인해야 한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Call c where c.id = :id")
    Optional<Call> findByIdForUpdate(@Param("id") Long id);

    boolean existsByRelationshipIdAndStatusIn(Long relationshipId, Collection<CallStatus> statuses);

    long countByRelationshipIdAndSenderAndStatusInAndCreatedAtAfter(
            Long relationshipId,
            CallSender sender,
            Collection<CallStatus> statuses,
            LocalDateTime createdAt);
}
