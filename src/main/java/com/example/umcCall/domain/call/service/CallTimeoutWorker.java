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
 * 끝나지 못한 통화를 걷어내는 스위퍼. {@code RINGING} → {@code MISSED}(부재중),
 * {@code PENDING}·{@code DIALING} → {@code CANCELED}(미접속), {@code IN_PROGRESS} → {@code COMPLETED}(시간 상한).
 * 미접속이 부재중이 아닌 이유는 사용자 부재가 아니라 서버/네트워크 사유이기 때문이다.
 *
 * <p>걷지 않으면 영구히 "진행 중"으로 남아 그 관계의 발신이 409로 막히고 proactive AI 발신도 영구 차단된다.
 * 게다가 진행 중 상태는 통화 목록에서 빠져 사용자 눈에도 안 보인다.
 *
 * <p>연결 대기 셋({@code RINGING}·{@code PENDING}·{@code DIALING})은 소켓이 없어 {@code finish()} 트리거가
 * 오지 않는 상태다. {@code IN_PROGRESS}는 반대로 <b>소켓이 살아 있을 수 있어</b> 마감 시 세션 정리가 따라야 한다
 * ({@code closeOverrunCall}이 이벤트를 발행한다).
 *
 * <p>루프는 트랜잭션 밖, 마감은 건당 서비스 트랜잭션이다 — 하나가 실패해도 나머지가 처리되고
 * 배치가 하나의 긴 트랜잭션·락으로 묶이지 않는다.
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
    private final long dialTimeoutSeconds;
    private final long maxCallMinutes;

    public CallTimeoutWorker(CallRepository callRepository,
                             CallService callService,
                             @Value("${call.timeout.ring-seconds:30}") long ringTimeoutSeconds,
                             @Value("${call.timeout.pending-seconds:60}") long pendingTimeoutSeconds,
                             @Value("${call.timeout.dial-seconds:60}") long dialTimeoutSeconds,
                             @Value("${call.timeout.max-call-minutes:60}") long maxCallMinutes) {
        this.callRepository = callRepository;
        this.callService = callService;
        this.ringTimeoutSeconds = ringTimeoutSeconds;
        this.pendingTimeoutSeconds = pendingTimeoutSeconds;
        this.dialTimeoutSeconds = dialTimeoutSeconds;
        this.maxCallMinutes = maxCallMinutes;
    }

    /**
     * 타임아웃을 넘긴 통화를 마감한다.
     * <p>폴링 주기는 벨 타임아웃보다 촘촘해야 판정이 제시간에 떨어진다.
     */
    @Scheduled(fixedDelayString = "${call.timeout.scheduler-delay-ms:10000}")
    public void closeTimedOutCalls() {
        LocalDateTime now = LocalDateTime.now();

        // ⚠ 기준 시각의 원점이 상태마다 다르다: 벨·발신은 createdAt, 미접속은 acceptedAt(받은 시각),
        // 시간 상한은 startedAt(통화 시작). 미접속이 원점을 공유하면 늦게 받은 사용자의 유예가 0으로 수렴한다.
        LocalDateTime ringThreshold = now.minusSeconds(ringTimeoutSeconds);
        LocalDateTime pendingThreshold = now.minusSeconds(pendingTimeoutSeconds);
        LocalDateTime dialThreshold = now.minusSeconds(dialTimeoutSeconds);
        LocalDateTime overrunThreshold = now.minusMinutes(maxCallMinutes);
        PageRequest batch = PageRequest.of(0, BATCH_SIZE);

        sweep(callRepository.findTimedOutIds(CallStatus.RINGING, ringThreshold, batch),
                ringThreshold, "부재중", callService::markMissed);
        sweep(callRepository.findStalePendingIds(CallStatus.PENDING, pendingThreshold, batch),
                pendingThreshold, "미접속 취소", callService::cancelStalePending);
        sweep(callRepository.findTimedOutIds(CallStatus.DIALING, dialThreshold, batch),
                dialThreshold, "발신 미접속 취소", callService::cancelStaleDialing);
        sweep(callRepository.findOverrunIds(CallStatus.IN_PROGRESS, overrunThreshold, batch),
                overrunThreshold, "시간 상한 마감", callService::closeOverrunCall);
    }

    private void sweep(List<Long> timedOutIds, LocalDateTime threshold, String action, Consumer<Long> close) {
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
