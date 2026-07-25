package com.example.umcCall.domain.call.service;

import com.example.umcCall.domain.call.enums.CallReservationStatus;
import com.example.umcCall.domain.call.repository.CallReservationRepository;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 예약 통화 스케줄러. 도달한 예약을 골라 {@link CallReservationService}에 한 건씩 넘긴다.
 *
 * <p>⚠ <b>이 클래스에는 트랜잭션이 없다</b> — 루프를 tx 밖에서 돌려야 예약 하나가 실패해도 나머지가
 * 처리되고, 배치 전체가 하나의 긴 트랜잭션·락으로 묶이지 않는다. 트랜잭션 경계는 서비스의 건당 메서드다.
 *
 * <p>정책 값(grace window)은 워커가 갖는다 — 리포지토리는 "이 범위를 조회한다"만 알게 해서
 * 시각 계산을 한곳에 모은다.
 */
@Service
@Slf4j
public class CallReservationWorker {

    /** 한 tick에 처리할 최대 건수. 밀린 예약이 많아도 tick 하나가 길어지지 않게 자른다(다음 tick이 이어받음). */
    private static final int BATCH_SIZE = 50;

    private final CallReservationRepository reservationRepository;
    private final CallReservationService reservationService;
    private final long graceMinutes;

    public CallReservationWorker(CallReservationRepository reservationRepository,
                                 CallReservationService reservationService,
                                 @Value("${call.reservation.grace-minutes:5}") long graceMinutes) {
        this.reservationRepository = reservationRepository;
        this.reservationService = reservationService;
        this.graceMinutes = graceMinutes;
    }

    /**
     * 도달한 예약을 발신하고, 너무 늦은 예약은 발신 없이 종결한다.
     * <p>{@code graceFrom} 하한이 재기동 직후의 몰림 발신을 막는다 — 그 하한보다 과거인 예약은
     * "지각한 전화"를 울리는 대신 CANCELED로 닫는다.
     */
    @Scheduled(fixedDelayString = "${call.reservation.scheduler-delay-ms:60000}")
    public void processDueReservations() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime graceFrom = now.minusMinutes(graceMinutes);
        PageRequest limit = PageRequest.of(0, BATCH_SIZE);

        reservationRepository
                .findDueIds(CallReservationStatus.SCHEDULED, graceFrom, now, limit)
                .forEach(reservationId -> runIsolated(reservationId, "발신",
                        () -> reservationService.fire(reservationId)));

        reservationRepository
                .findExpiredIds(CallReservationStatus.SCHEDULED, graceFrom, limit)
                .forEach(reservationId -> runIsolated(reservationId, "만료 종결",
                        () -> reservationService.expire(reservationId)));
    }

    /** 예약 하나의 실패가 나머지 처리를 막지 않게 격리한다(로그만 남기고 계속). */
    private void runIsolated(Long reservationId, String action, Runnable task) {
        try {
            task.run();
        } catch (RuntimeException exception) {
            log.error("예약 {} 실패. reservationId={}", action, reservationId, exception);
        }
    }
}
