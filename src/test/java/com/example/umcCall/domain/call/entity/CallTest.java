package com.example.umcCall.domain.call.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.umcCall.domain.call.enums.CallSender;
import com.example.umcCall.domain.call.enums.CallStatus;
import org.junit.jupiter.api.Test;

/** AI 발신 통화의 거절 전이 검증. reject는 API로 직접 호출되므로 엔티티가 상태를 스스로 막는다. */
class CallTest {

    /** AI 발신 초기 상태 = RINGING. */
    private Call ringingCall() {
        return Call.builder().sender(CallSender.AI).build();
    }

    @Test
    void reject는_RINGING을_REJECTED로_전이한다() {
        Call call = ringingCall();

        call.reject();

        assertThat(call.getStatus()).isEqualTo(CallStatus.REJECTED);
    }

    @Test
    void reject는_연결_시간을_남기지_않는다() {
        Call call = ringingCall();

        call.reject();

        assertThat(call.getStartedAt()).isNull();
        assertThat(call.getEndedAt()).isNull();
        assertThat(call.getCallTime()).isNull();
    }

    @Test
    void reject는_연결된_통화를_거절하지_못한다() {
        Call call = ringingCall();
        call.connect(); // IN_PROGRESS

        assertThatThrownBy(call::reject)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IN_PROGRESS");
    }

    @Test
    void reject는_이미_거절된_통화에_다시_적용되지_않는다() {
        Call call = ringingCall();
        call.reject();

        assertThatThrownBy(call::reject)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("REJECTED");
    }
}
