package com.example.umcCall.domain.call.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.umcCall.domain.call.enums.CallReservationStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** 예약 상태 전이 검증 — 한 예약은 한 번만 울려야 하므로 SCHEDULED 밖에서는 전이를 허용하지 않는다. */
class CallReservationTest {

    private CallReservation scheduled() {
        return CallReservation.builder()
                .scheduledAt(LocalDateTime.now().plusHours(1))
                .build();
    }

    @Test
    void 새_예약은_SCHEDULED로_시작한다() {
        assertThat(scheduled().getStatus()).isEqualTo(CallReservationStatus.SCHEDULED);
    }

    @Test
    void markFired는_SCHEDULED를_FIRED로_전이한다() {
        CallReservation reservation = scheduled();

        reservation.markFired();

        assertThat(reservation.getStatus()).isEqualTo(CallReservationStatus.FIRED);
    }

    @Test
    void cancel은_SCHEDULED를_CANCELED로_전이한다() {
        CallReservation reservation = scheduled();

        reservation.cancel();

        assertThat(reservation.getStatus()).isEqualTo(CallReservationStatus.CANCELED);
    }

    @Test
    void 이미_발신한_예약은_다시_발신되지_않는다() {
        CallReservation reservation = scheduled();
        reservation.markFired();

        // 중복 tick·다중 인스턴스가 같은 예약을 집어도 두 번 울리지 않아야 한다.
        assertThatThrownBy(reservation::markFired)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FIRED");
    }

    @Test
    void 이미_발신한_예약은_취소되지_않는다() {
        CallReservation reservation = scheduled();
        reservation.markFired();

        assertThatThrownBy(reservation::cancel)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FIRED");
    }

    @Test
    void 이미_취소된_예약은_발신되지_않는다() {
        CallReservation reservation = scheduled();
        reservation.cancel();

        assertThatThrownBy(reservation::markFired)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CANCELED");
    }
}
