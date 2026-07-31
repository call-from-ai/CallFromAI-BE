package com.example.umcCall.domain.relationship.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class RelationshipStatusTest {

    @Test
    void 완료된_통화를_누적하고_날짜가_이어지면_연속일수를_증가시킨다() {
        RelationshipStatus status = RelationshipStatus.builder().relationship(null).build();

        status.recordCompletedCall(LocalDateTime.of(2026, 7, 29, 10, 0));
        status.recordCompletedCall(LocalDateTime.of(2026, 7, 30, 10, 0));
        status.recordCompletedCall(LocalDateTime.of(2026, 7, 30, 20, 0));

        assertThat(status.getTotalCallCount()).isEqualTo(3);
        assertThat(status.getCallStreakDays()).isEqualTo(2);
        assertThat(status.getLastCallAt()).isEqualTo(LocalDateTime.of(2026, 7, 30, 20, 0));
    }

    @Test
    void 통화하지_않은_날이_있으면_연속일수를_다시_시작한다() {
        RelationshipStatus status = RelationshipStatus.builder().relationship(null).build();
        status.recordCompletedCall(LocalDateTime.of(2026, 7, 28, 10, 0));

        status.recordCompletedCall(LocalDateTime.of(2026, 7, 30, 10, 0));

        assertThat(status.getTotalCallCount()).isEqualTo(2);
        assertThat(status.getCallStreakDays()).isEqualTo(1);
    }
}
