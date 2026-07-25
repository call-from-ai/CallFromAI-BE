package com.example.umcCall.domain.call.service;

import com.example.umcCall.domain.call.enums.CallStatus;
import com.example.umcCall.domain.call.repository.CallRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 연결되지 못한 통화를 걷어내는 스위퍼. {@code RINGING} → {@code MISSED}(부재중),
 * {@code PENDING} → {@code CANCELED}(받았지만 미접속 — 서버/네트워크 사유라 부재중이 아니다).
 *
 * <p>⚠ 두 상태는 소켓이 없어 {@code finish()} 트리거가 오지 않는다. 걷지 않으면 영구히 "진행 중"으로 남고,
 * 발신 중복 방어가 그 상태를 보므로 <b>그 관계의 이후 모든 예약이 막힌다</b>.
 *
 * <p>{@link CallReservationWorker}와 같은 구조 — 루프는 트랜잭션 밖, 마감은 건당 서비스 트랜잭션.
 */
@Service
@Slf4j
public class CallTimeoutWorker {

    /** 한 tick에 마감할 최대 건수(상태별). */
    private static final int BATCH_SIZE = 50;

    private final CallRepository callRepository;
    private final CallService callService;
    private final long ringTimeoutSeconds;
    private final long pendingTimeoutSeconds;

    public CallTimeoutWorker(CallRepository callRepository,
                             CallService callService,
                             @Value("${call.timeout.ring-seconds:30}") long ringTimeoutSeconds,
                             @Value("${call.timeout.pending-seconds:60}") long pendingTimeoutSeconds) {
        this.callRepository = callRepository;
        this.callService = callService;
        this.ringTimeoutSeconds = ringTimeoutSeconds;
        this.pendingTimeoutSeconds = pendingTimeoutSeconds;
    }

    /**
     * 타임아웃을 넘긴 통화를 마감한다.
     * <p>폴링 주기는 벨 타임아웃보다 촘촘해야 판정이 제시간에 떨어진다.
     */
    @Scheduled(fixedDelayString = "${call.timeout.scheduler-delay-ms:10000}")
    public void closeTimedOutCalls() {
        LocalDateTime now = LocalDateTime.now();

        sweep(CallStatus.RINGING, now.minusSeconds(ringTimeoutSeconds), "부재중", callService::markMissed);
        sweep(CallStatus.PENDING, now.minusSeconds(pendingTimeoutSeconds), "미접속 취소",
                callService::cancelStalePending);
    }

    private void sweep(CallStatus status, LocalDateTime threshold, String action, Consumer<Long> close) {
        List<Long> timedOutIds = callRepository.findTimedOutIds(
                status, threshold, PageRequest.of(0, BATCH_SIZE));

        for (Long callId : timedOutIds) {
            try {
                close.accept(callId);
                log.info("[Call] 통화 {} 마감. callId={}, 기준={}", action, callId, threshold);
            } catch (RuntimeException exception) {
                // 하나가 실패해도 나머지는 걷는다.
                log.error("[Call] 통화 {} 마감 실패. callId={}", action, callId, exception);
            }
        }
    }
}
