package com.example.umcCall.domain.call.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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

    @Mock private CallRepository callRepository;
    @Mock private CallService callService;

    private CallTimeoutWorker worker;

    @BeforeEach
    void setUp() {
        worker = new CallTimeoutWorker(
                callRepository, callService, RING_TIMEOUT_SECONDS, PENDING_TIMEOUT_SECONDS);
    }

    private void givenTimedOut(List<Long> ringingIds, List<Long> pendingIds) {
        when(callRepository.findTimedOutIds(eq(CallStatus.RINGING), any(), any(Pageable.class)))
                .thenReturn(ringingIds);
        when(callRepository.findTimedOutIds(eq(CallStatus.PENDING), any(), any(Pageable.class)))
                .thenReturn(pendingIds);
    }

    @Test
    void 벨_타임아웃을_넘긴_통화는_부재중_미접속은_취소로_마감한다() {
        givenTimedOut(List.of(1L, 2L), List.of(7L));

        worker.closeTimedOutCalls();

        verify(callService).markMissed(1L);
        verify(callService).markMissed(2L);
        verify(callService).cancelStalePending(7L);
    }

    @Test
    void 상태별로_다른_타임아웃_기준을_쓴다() {
        givenTimedOut(List.of(), List.of());
        LocalDateTime before = LocalDateTime.now();

        worker.closeTimedOutCalls();

        LocalDateTime after = LocalDateTime.now();
        ArgumentCaptor<LocalDateTime> ringThreshold = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> pendingThreshold = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(callRepository).findTimedOutIds(
                eq(CallStatus.RINGING), ringThreshold.capture(), any(Pageable.class));
        verify(callRepository).findTimedOutIds(
                eq(CallStatus.PENDING), pendingThreshold.capture(), any(Pageable.class));

        // 벨은 30초, 받은 뒤 미접속은 60초(wsTicket TTL 30초 + 여유) — 기준이 서로 달라야 한다.
        assertThat(ringThreshold.getValue()).isBetween(
                before.minusSeconds(RING_TIMEOUT_SECONDS), after.minusSeconds(RING_TIMEOUT_SECONDS));
        assertThat(pendingThreshold.getValue()).isBetween(
                before.minusSeconds(PENDING_TIMEOUT_SECONDS), after.minusSeconds(PENDING_TIMEOUT_SECONDS));
        assertThat(pendingThreshold.getValue()).isBefore(ringThreshold.getValue());
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
