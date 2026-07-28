package com.example.umcCall.domain.call.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.umcCall.domain.call.enums.CallReservationStatus;
import com.example.umcCall.domain.call.repository.CallReservationRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

/** 폴링 tick의 조회 범위(grace window)와 실패 격리 검증. */
@ExtendWith(MockitoExtension.class)
class CallReservationWorkerTest {

    private static final long GRACE_MINUTES = 5;

    @Mock private CallReservationRepository reservationRepository;
    @Mock private CallReservationService reservationService;

    private CallReservationWorker worker;

    @BeforeEach
    void setUp() {
        worker = new CallReservationWorker(reservationRepository, reservationService, GRACE_MINUTES);
    }

    private void givenDueIds(List<Long> dueIds, List<Long> expiredIds) {
        when(reservationRepository.findDueIds(any(), any(), any(), any())).thenReturn(dueIds);
        when(reservationRepository.findExpiredIds(any(), any(), any())).thenReturn(expiredIds);
    }

    @Test
    void 도달한_예약은_발신하고_grace를_넘긴_예약은_만료_종결한다() {
        givenDueIds(List.of(1L, 2L), List.of(9L));

        worker.processDueReservations();

        verify(reservationService).fire(1L);
        verify(reservationService).fire(2L);
        verify(reservationService).expire(9L);
    }

    @Test
    void 조회_범위는_graceFrom부터_now까지다() {
        givenDueIds(List.of(), List.of());
        LocalDateTime before = LocalDateTime.now();

        worker.processDueReservations();

        ArgumentCaptor<LocalDateTime> graceFrom = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> now = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(reservationRepository).findDueIds(
                eq(CallReservationStatus.SCHEDULED), graceFrom.capture(), now.capture(), any(Pageable.class));

        assertThat(now.getValue()).isAfterOrEqualTo(before);
        // 하한 = now - graceMinutes. 이 하한이 재기동 직후 몰림 발신을 막는다.
        assertThat(Duration.between(graceFrom.getValue(), now.getValue()).toMinutes())
                .isEqualTo(GRACE_MINUTES);
    }

    @Test
    void 만료_조회는_발신_조회와_같은_graceFrom을_쓴다() {
        givenDueIds(List.of(), List.of());

        worker.processDueReservations();

        ArgumentCaptor<LocalDateTime> dueGraceFrom = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> expiredGraceFrom = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(reservationRepository).findDueIds(any(), dueGraceFrom.capture(), any(), any(Pageable.class));
        verify(reservationRepository).findExpiredIds(
                eq(CallReservationStatus.SCHEDULED), expiredGraceFrom.capture(), any(Pageable.class));

        // 두 쿼리가 같은 경계를 공유해야 겹치거나 빠지는 예약이 없다.
        assertThat(expiredGraceFrom.getValue()).isEqualTo(dueGraceFrom.getValue());
    }

    @Test
    void 예약_하나가_실패해도_나머지는_처리된다() {
        givenDueIds(List.of(1L, 2L, 3L), List.of());
        doThrow(new RuntimeException("발신 실패")).when(reservationService).fire(2L);

        worker.processDueReservations();

        verify(reservationService).fire(1L);
        verify(reservationService).fire(3L);
    }

    @Test
    void 처리할_예약이_없으면_서비스를_부르지_않는다() {
        givenDueIds(List.of(), List.of());

        worker.processDueReservations();

        verifyNoInteractions(reservationService);
    }
}
