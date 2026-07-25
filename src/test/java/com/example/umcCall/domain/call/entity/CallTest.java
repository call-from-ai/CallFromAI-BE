package com.example.umcCall.domain.call.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.umcCall.domain.call.enums.CallSender;
import com.example.umcCall.domain.call.enums.CallStatus;
import org.junit.jupiter.api.Test;

/** AI 발신 통화의 수락·거절 전이 검증. 둘 다 API로 직접 호출되므로 엔티티가 상태를 스스로 막는다. */
class CallTest {

    /** AI 발신 초기 상태 = RINGING. */
    private Call ringingCall() {
        return Call.builder().sender(CallSender.AI).build();
    }

    @Test
    void accept는_RINGING을_PENDING으로_전이한다() {
        Call call = ringingCall();

        call.accept();

        assertThat(call.getStatus()).isEqualTo(CallStatus.PENDING);
        // 아직 오디오가 흐르지 않으므로 통화 시작 시각을 찍지 않는다.
        assertThat(call.getStartedAt()).isNull();
    }

    @Test
    void connect는_PENDING을_IN_PROGRESS로_전이하고_startedAt을_채운다() {
        Call call = ringingCall();
        call.accept(); // PENDING

        call.connect();

        assertThat(call.getStatus()).isEqualTo(CallStatus.IN_PROGRESS);
        assertThat(call.getStartedAt()).isNotNull();
    }

    @Test
    void connect는_accept를_거치지_않은_RINGING을_연결하지_못한다() {
        Call call = ringingCall();

        assertThatThrownBy(call::connect)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RINGING");
    }

    @Test
    void accept는_이미_받은_통화에_다시_적용되지_않는다() {
        Call call = ringingCall();
        call.accept();

        assertThatThrownBy(call::accept)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PENDING");
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
        call.accept();
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
