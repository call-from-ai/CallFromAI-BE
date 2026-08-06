package com.example.umcCall.domain.proactive.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.umcCall.domain.character.enums.PreferTime;
import com.example.umcCall.domain.proactive.enums.AttachmentLevel;
import com.example.umcCall.domain.proactive.enums.ProactiveAction;
import com.example.umcCall.domain.proactive.enums.ProactiveRelationshipState;
import com.example.umcCall.domain.proactive.enums.RecentResponse;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ProactiveContactPolicyTest {

    private final ProactiveContactPolicy policy =
            new ProactiveContactPolicy(new PreferredContactTimePolicy());
    private final LocalDateTime now = LocalDateTime.of(2026, 7, 23, 20, 0);

    @Test
    void normal_2시간의_랜덤_범위는_1시간40분부터_2시간30분이다() {
        var minimum = policy.decide(context(now.minusHours(3), AttachmentLevel.NORMAL,
                ProactiveRelationshipState.NORMAL, RecentResponse.POSITIVE, 0), 0.0);
        var maximum = policy.decide(context(now.minusHours(3), AttachmentLevel.NORMAL,
                ProactiveRelationshipState.NORMAL, RecentResponse.POSITIVE, 0), 1.0);

        assertThat(minimum.action()).isEqualTo(ProactiveAction.CHAT);
        assertThat(maximum.action()).isEqualTo(ProactiveAction.CHAT);

        var notYetMinimum = policy.decide(context(now.minusMinutes(99), AttachmentLevel.NORMAL,
                ProactiveRelationshipState.NORMAL, RecentResponse.POSITIVE, 0), 0.0);
        var notYetMaximum = policy.decide(context(now.minusMinutes(149), AttachmentLevel.NORMAL,
                ProactiveRelationshipState.NORMAL, RecentResponse.POSITIVE, 0), 1.0);
        assertThat(notYetMinimum.nextCheckAt()).isEqualTo(now.minusMinutes(99).plusMinutes(100));
        assertThat(notYetMaximum.nextCheckAt()).isEqualTo(now.minusMinutes(149).plusMinutes(150));
    }

    @Test
    void conflict는_전화가_아니라_갈등_정리_채팅만_허용한다() {
        var decision = policy.decide(context(now.minusHours(7), AttachmentLevel.LOW,
                ProactiveRelationshipState.CONFLICT, RecentResponse.POSITIVE, 0), 0.0);

        assertThat(decision.action()).isEqualTo(ProactiveAction.CHAT);
        assertThat(decision.contactReason()).isEqualTo("CONFLICT_REPAIR");
    }

    @Test
    void 메인_캐릭터도_기본은_채팅이고_일부만_통화한다() {
        var chat = policy.decide(callableContext(), 0.5, 0.25);
        var call = policy.decide(callableContext(), 0.5, 0.2499);

        assertThat(chat.action()).isEqualTo(ProactiveAction.CHAT);
        assertThat(call.action()).isEqualTo(ProactiveAction.CALL);
    }

    @Test
    void 비메인은_통화_선택값이어도_항상_채팅한다() {
        var nonMain = policy.decide(context(now.minusHours(3), AttachmentLevel.NORMAL,
                ProactiveRelationshipState.NORMAL, RecentResponse.POSITIVE, 0), 0.5, 0.1);

        assertThat(nonMain.action()).isEqualTo(ProactiveAction.CHAT);
    }

    @Test
    void 미응답_2회는_다음_선호_시간으로_연기하고_3회는_중단한다() {
        var twice = policy.decide(context(now.minusHours(10), AttachmentLevel.HIGH,
                ProactiveRelationshipState.NORMAL, RecentResponse.NO_RESPONSE, 2), 0.5);
        var three = policy.decide(context(now.minusHours(10), AttachmentLevel.HIGH,
                ProactiveRelationshipState.NORMAL, RecentResponse.NO_RESPONSE, 3), 0.5);

        assertThat(twice.action()).isEqualTo(ProactiveAction.DEFER);
        assertThat(twice.reason()).isEqualTo("TWO_NO_RESPONSES");
        assertThat(three.action()).isEqualTo(ProactiveAction.BLOCKED);
        assertThat(three.reason()).isEqualTo("THREE_NO_RESPONSES");
    }

    @Test
    void attachment_점수_경계는_4와_7이다() {
        assertThat(AttachmentLevel.from(3.99)).isEqualTo(AttachmentLevel.LOW);
        assertThat(AttachmentLevel.from(4.0)).isEqualTo(AttachmentLevel.NORMAL);
        assertThat(AttachmentLevel.from(6.99)).isEqualTo(AttachmentLevel.NORMAL);
        assertThat(AttachmentLevel.from(7.0)).isEqualTo(AttachmentLevel.HIGH);
    }

    private ProactiveContactPolicy.Context context(
            LocalDateTime lastContactAt,
            AttachmentLevel attachment,
            ProactiveRelationshipState state,
            RecentResponse response,
            int noResponseCount
    ) {
        return new ProactiveContactPolicy.Context(
                now, lastContactAt, null, true, false, false, false,
                PreferTime.ANYTIME, attachment, state, response,
                noResponseCount, false, false, false, false, null);
    }

    private ProactiveContactPolicy.Context callableContext() {
        return new ProactiveContactPolicy.Context(
                now, now.minusHours(3), null, true, false, false, false,
                PreferTime.ANYTIME, AttachmentLevel.NORMAL,
                ProactiveRelationshipState.NORMAL, RecentResponse.POSITIVE,
                0, false, false, true, false, null);
    }
}
