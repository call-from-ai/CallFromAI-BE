package com.example.umcCall.domain.call.repository;

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
 * 예약 통화 저장/조회. 스케줄러가 도달한 예약을 집어 AI 발신(Call 생성)으로 옮기는 데 쓴다.
 * <p>조회 조건이 모두 {@code status='SCHEDULED'} + {@code scheduled_at} 범위라
 * 복합 인덱스 {@code idx_reservation_status_scheduled_at (status, scheduled_at)}를 그대로 탄다.
 */
public interface CallReservationRepository extends JpaRepository<CallReservation, Long> {

    /**
     * 발신할 예약 id를 예약 시각 순으로 조회한다. {@code graceFrom} 이후 ~ {@code now} 이하만 본다.
     * <p>⚠ 하한(graceFrom)이 있는 이유: 서버가 죽어 있다가 재기동하면 {@code scheduled_at <= now}에
     * 과거 예약이 전부 걸려 한꺼번에 울린다(새벽 예약이 아침에 오는 문제). 하한 밖은
     * {@link #findExpiredIds}가 발신 없이 종결한다.
     * <p>엔티티가 아니라 id만 가져온다 — 실제 처리는 건당 {@link #findByIdForUpdate}로 다시 잠그고 하므로
     * 여기서 엔티티를 들고 있을 이유가 없다(proactive 스케줄러와 동일 패턴).
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
     * 처리 대상 예약을 비관적 락으로 집는다(claim). 다중 인스턴스에서 같은 예약을 두 번 울리지 않게 한다.
     * <p>락만으로는 부족해서, 호출부가 잠근 뒤 상태가 여전히 SCHEDULED인지 다시 확인해야 한다
     * (먼저 집은 쪽이 이미 FIRED로 바꿨을 수 있다).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from CallReservation r where r.id = :id")
    Optional<CallReservation> findByIdForUpdate(@Param("id") Long id);
}
