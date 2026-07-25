package com.example.umcCall.domain.call.repository;

import com.example.umcCall.domain.call.dto.response.CallReservationItem;
import com.example.umcCall.domain.call.entity.CallReservation;
import com.example.umcCall.domain.call.enums.CallReservationStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 예약 통화 저장/조회. 조회 조건이 모두 {@code status} + {@code scheduled_at} 범위라
 * 복합 인덱스 {@code idx_reservation_status_scheduled_at}를 그대로 탄다.
 */
public interface CallReservationRepository extends JpaRepository<CallReservation, Long> {

    /**
     * 발신할 예약 id를 예약 시각 순으로 조회한다({@code graceFrom} 이상 ~ {@code now} 이하).
     * <p>⚠ 하한이 있는 이유: 재기동 시 {@code scheduled_at <= now}만 보면 밀린 과거 예약이 한꺼번에
     * 울린다(새벽 예약이 아침에 오는 문제). 하한 밖은 {@link #findExpiredIds}가 발신 없이 종결한다.
     * <p>id만 가져온다 — 처리는 건당 {@link #findByIdForUpdate}로 다시 잠그고 한다(proactive와 동일 패턴).
     */
    @Query("""
            select r.id from CallReservation r
            where r.status = :status
              and r.scheduledAt <= :now
              and r.scheduledAt >= :graceFrom
            order by r.scheduledAt, r.id
            """)
    List<Long> findDueIds(@Param("status") CallReservationStatus status,
                          @Param("graceFrom") LocalDateTime graceFrom,
                          @Param("now") LocalDateTime now,
                          Pageable pageable);

    /** grace window를 넘겨 발신하지 않고 종결할 예약 id. */
    @Query("""
            select r.id from CallReservation r
            where r.status = :status
              and r.scheduledAt < :graceFrom
            order by r.scheduledAt, r.id
            """)
    List<Long> findExpiredIds(@Param("status") CallReservationStatus status,
                              @Param("graceFrom") LocalDateTime graceFrom,
                              Pageable pageable);

    /**
     * 회원의 대기 중 예약을 주어진 창 안에서 <b>가까운 시각부터</b> 조회한다(캐릭터 조인 + DTO 프로젝션).
     * <p>정렬이 통화 목록(최신순)과 반대다 — 예약은 미래의 약속이라 곧 올 전화가 위여야 한다.
     * 창의 경계는 호출부가 정한다.
     */
    @Query("""
            select new com.example.umcCall.domain.call.dto.response.CallReservationItem(
                r.id, ch.id, ch.firstName, ch.imageUrl, r.scheduledAt)
            from CallReservation r
                join r.relationship rel
                join rel.character ch
            where rel.memberId = :memberId
              and r.status = :status
              and r.scheduledAt >= :from
              and r.scheduledAt < :to
            order by r.scheduledAt asc
            """)
    List<CallReservationItem> findMyReservations(@Param("memberId") Long memberId,
                                                 @Param("status") CallReservationStatus status,
                                                 @Param("from") LocalDateTime from,
                                                 @Param("to") LocalDateTime to);

    /**
     * 관계에 대기 중인 예약이 이미 있는지 — 생성 시 "관계당 SCHEDULED 1건" 규칙을 본다.
     * <p>{@code relationship_id} FK 인덱스로 좁힌 뒤 상태를 걸러낸다(예약은 관계당 소수라 충분하다).
     */
    boolean existsByRelationshipIdAndStatus(Long relationshipId, CallReservationStatus status);

    /**
     * 처리 대상 예약을 비관적 락으로 집는다. 다중 인스턴스가 같은 예약을 두 번 울리지 않게 한다.
     * <p>락만으론 부족하다 — 호출부가 락 뒤 상태가 여전히 SCHEDULED인지 확인해야 한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from CallReservation r where r.id = :id")
    Optional<CallReservation> findByIdForUpdate(@Param("id") Long id);
}
