package com.example.umcCall.domain.call.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.umcCall.domain.call.enums.CallStatus;
import com.example.umcCall.domain.call.repository.CallRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

/** 스위퍼의 타임아웃 기준과 실패 격리 검증. */
@ExtendWith(MockitoExtension.class)
class CallTimeoutWorkerTest {

    private static final long RING_TIMEOUT_SECONDS = 30;
    private static final long PENDING_TIMEOUT_SECONDS = 60;
    private static final long DIAL_TIMEOUT_SECONDS = 90;
    private static final long MAX_CALL_MINUTES = 60;

    @Mock private CallRepository callRepository;
    @Mock private CallService callService;

    private CallTimeoutWorker worker;

    @BeforeEach
    void setUp() {
        worker = new CallTimeoutWorker(callRepository, callService, RING_TIMEOUT_SECONDS,
                PENDING_TIMEOUT_SECONDS, DIAL_TIMEOUT_SECONDS, MAX_CALL_MINUTES);
    }

    private void givenTimedOut(List<Long> ringingIds, List<Long> pendingIds) {
        givenTimedOut(ringingIds, pendingIds, List.of());
    }

    private void givenTimedOut(List<Long> ringingIds, List<Long> pendingIds, List<Long> dialingIds) {
        givenTimedOut(ringingIds, pendingIds, dialingIds, List.of());
    }

    private void givenTimedOut(List<Long> ringingIds, List<Long> pendingIds,
                               List<Long> dialingIds, List<Long> overrunIds) {
        when(callRepository.findOverrunIds(eq(CallStatus.IN_PROGRESS), any(), any(Pageable.class)))
                .thenReturn(overrunIds);
        when(callRepository.findTimedOutIds(eq(CallStatus.RINGING), any(), any(Pageable.class)))
                .thenReturn(ringingIds);
        when(callRepository.findStalePendingIds(eq(CallStatus.PENDING), any(), any(Pageable.class)))
                .thenReturn(pendingIds);
        when(callRepository.findTimedOutIds(eq(CallStatus.DIALING), any(), any(Pageable.class)))
                .thenReturn(dialingIds);
    }

    /** 시간 상한은 startedAt(통화 시작) 기준 — 대기 시간이 아니라 실제 통화 길이를 잰다. */
    @Test
    void 시간_상한을_넘긴_통화는_startedAt_기준으로_마감한다() {
        givenTimedOut(List.of(), List.of(), List.of(), List.of(42L));
        LocalDateTime before = LocalDateTime.now();

        worker.closeTimedOutCalls();

        LocalDateTime after = LocalDateTime.now();
        verify(callService).closeOverrunCall(42L);

        ArgumentCaptor<LocalDateTime> threshold = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(callRepository).findOverrunIds(
                eq(CallStatus.IN_PROGRESS), threshold.capture(), any(Pageable.class));
        assertThat(threshold.getValue()).isBetween(
                before.minusMinutes(MAX_CALL_MINUTES), after.minusMinutes(MAX_CALL_MINUTES));
    }

    @Test
    void 벨_타임아웃을_넘긴_통화는_부재중_미접속은_취소로_마감한다() {
        givenTimedOut(List.of(1L, 2L), List.of(7L), List.of(9L));

        worker.closeTimedOutCalls();

        verify(callService).markMissed(1L);
        verify(callService).markMissed(2L);
        verify(callService).cancelStalePending(7L);
        verify(callService).cancelStaleDialing(9L);
    }

    @Test
    void 상태별로_다른_타임아웃_기준을_쓴다() {
        givenTimedOut(List.of(), List.of());
        LocalDateTime before = LocalDateTime.now();

        worker.closeTimedOutCalls();

        LocalDateTime after = LocalDateTime.now();
        ArgumentCaptor<LocalDateTime> ringThreshold = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> pendingThreshold = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> dialThreshold = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(callRepository).findTimedOutIds(
                eq(CallStatus.RINGING), ringThreshold.capture(), any(Pageable.class));
        verify(callRepository).findStalePendingIds(
                eq(CallStatus.PENDING), pendingThreshold.capture(), any(Pageable.class));
        verify(callRepository).findTimedOutIds(
                eq(CallStatus.DIALING), dialThreshold.capture(), any(Pageable.class));

        // 세 기준이 서로 독립이어야 한다(설정값이 다르면 임계값도 달라진다).
        assertThat(ringThreshold.getValue()).isBetween(
                before.minusSeconds(RING_TIMEOUT_SECONDS), after.minusSeconds(RING_TIMEOUT_SECONDS));
        assertThat(pendingThreshold.getValue()).isBetween(
                before.minusSeconds(PENDING_TIMEOUT_SECONDS), after.minusSeconds(PENDING_TIMEOUT_SECONDS));
        assertThat(dialThreshold.getValue()).isBetween(
                before.minusSeconds(DIAL_TIMEOUT_SECONDS), after.minusSeconds(DIAL_TIMEOUT_SECONDS));
        assertThat(pendingThreshold.getValue()).isBefore(ringThreshold.getValue());
        assertThat(dialThreshold.getValue()).isBefore(pendingThreshold.getValue());
    }

    /** 발신은 "생성 = 대기 시작"이라 원점이 어긋날 여지가 없다. */
    @Test
    void 발신_미접속은_createdAt_기준으로_고른다() {
        givenTimedOut(List.of(), List.of());

        worker.closeTimedOutCalls();

        verify(callRepository).findTimedOutIds(eq(CallStatus.DIALING), any(), any(Pageable.class));
        verify(callRepository, never()).findStalePendingIds(
                eq(CallStatus.DIALING), any(), any(Pageable.class));
    }

    /** 회귀 방어 — createdAt 기준으로 되돌리면 늦게 받은 사용자가 받자마자 취소된다. */
    @Test
    void 미접속_대상은_createdAt이_아니라_acceptedAt_기준으로_고른다() {
        givenTimedOut(List.of(), List.of());

        worker.closeTimedOutCalls();

        verify(callRepository).findStalePendingIds(eq(CallStatus.PENDING), any(), any(Pageable.class));
        verify(callRepository, never()).findTimedOutIds(
                eq(CallStatus.PENDING), any(), any(Pageable.class));
    }

    @Test
    void 하나가_실패해도_나머지는_마감한다() {
        givenTimedOut(List.of(1L, 2L, 3L), List.of());
        doThrow(new RuntimeException("마감 실패")).when(callService).markMissed(2L);

        worker.closeTimedOutCalls();

        verify(callService).markMissed(1L);
        verify(callService).markMissed(3L);
    }

    @Test
    void 마감할_통화가_없으면_서비스를_부르지_않는다() {
        givenTimedOut(List.of(), List.of());

        worker.closeTimedOutCalls();

        verifyNoInteractions(callService);
    }
}
