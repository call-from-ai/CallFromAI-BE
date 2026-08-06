package com.example.umcCall.domain.proactive.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.umcCall.domain.character.enums.PreferTime;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class PreferredContactTimePolicyTest {

    private final PreferredContactTimePolicy policy = new PreferredContactTimePolicy();

    @Test
    void morning은_8시부터_12시_직전까지다() {
        assertThat(policy.evaluate(PreferTime.MORNING,
                LocalDateTime.of(2026, 7, 23, 7, 59)).preferred()).isFalse();
        assertThat(policy.evaluate(PreferTime.MORNING,
                LocalDateTime.of(2026, 7, 23, 8, 0)).preferred()).isTrue();
        assertThat(policy.evaluate(PreferTime.MORNING,
                LocalDateTime.of(2026, 7, 23, 11, 59)).preferred()).isTrue();
        assertThat(policy.evaluate(PreferTime.MORNING,
                LocalDateTime.of(2026, 7, 23, 12, 0)).preferred()).isFalse();
    }

    @Test
    void day는_13시부터_18시_직전까지다() {
        assertThat(policy.evaluate(PreferTime.DAY,
                LocalDateTime.of(2026, 7, 23, 12, 59)).preferred()).isFalse();
        assertThat(policy.evaluate(PreferTime.DAY,
                LocalDateTime.of(2026, 7, 23, 13, 0)).preferred()).isTrue();
        assertThat(policy.evaluate(PreferTime.DAY,
                LocalDateTime.of(2026, 7, 23, 17, 59)).preferred()).isTrue();
        assertThat(policy.evaluate(PreferTime.DAY,
                LocalDateTime.of(2026, 7, 23, 18, 0)).preferred()).isFalse();
    }

    @Test
    void late_evening은_19시부터_자정_직전까지다() {
        assertThat(policy.evaluate(PreferTime.LATE_EVENING,
                LocalDateTime.of(2026, 7, 23, 18, 59)).preferred()).isFalse();
        assertThat(policy.evaluate(PreferTime.LATE_EVENING,
                LocalDateTime.of(2026, 7, 23, 19, 0)).preferred()).isTrue();
        assertThat(policy.evaluate(PreferTime.LATE_EVENING,
                LocalDateTime.of(2026, 7, 23, 23, 59)).preferred()).isTrue();
    }

    @Test
    void 비선호_시간이면_다음_선호_구간_시작을_반환한다() {
        var result = policy.evaluate(PreferTime.DAY,
                LocalDateTime.of(2026, 7, 23, 20, 0));

        assertThat(result.preferred()).isFalse();
        assertThat(result.nextPreferredTime())
                .isEqualTo(LocalDateTime.of(2026, 7, 24, 13, 0));
    }

    @Test
    void anytime은_항상_선호_시간이다() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 23, 3, 0);
        assertThat(policy.evaluate(PreferTime.ANYTIME, now))
                .isEqualTo(new PreferredContactTimePolicy.Result(true, now));
    }

    @Test
    void 비선호_후보는_70퍼센트에서_다음_선호시간으로_옮긴다() {
        var candidate = LocalDateTime.of(2026, 7, 23, 20, 0);

        assertThat(policy.adjustCandidate(PreferTime.MORNING, candidate, 0.69))
                .isEqualTo(LocalDateTime.of(2026, 7, 24, 8, 0));
    }

    @Test
    void 비선호_후보는_30퍼센트에서_원래_시간을_유지한다() {
        var candidate = LocalDateTime.of(2026, 7, 23, 20, 0);

        assertThat(policy.adjustCandidate(PreferTime.MORNING, candidate, 0.70))
                .isEqualTo(candidate);
    }
}
