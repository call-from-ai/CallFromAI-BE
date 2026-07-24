package com.example.umcCall.domain.call.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.umcCall.domain.call.entity.Call;
import com.example.umcCall.domain.call.enums.CallSender;
import com.example.umcCall.domain.call.enums.CallStatus;
import com.example.umcCall.domain.call.exception.CallErrorCode;
import com.example.umcCall.domain.call.exception.CallException;
import com.example.umcCall.domain.call.repository.CallRepository;
import com.example.umcCall.domain.call.ticket.WsTicketStore;
import com.example.umcCall.domain.relationship.repository.RelationshipRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 통화 상태 전이 위임(connect/finish) 검증. 엔티티 전이는 실제 {@link Call}로 확인한다. */
@ExtendWith(MockitoExtension.class)
class CallServiceTest {

    private static final Long CALL_ID = 1L;

    @Mock private RelationshipRepository relationshipRepository;
    @Mock private CallRepository callRepository;
    @Mock private WsTicketStore wsTicketStore;

    @InjectMocks private CallService callService;

    /** 사용자 발신 초기 상태 = DIALING. */
    private Call dialingCall() {
        return Call.builder().sender(CallSender.USER).build();
    }

    @Test
    void connect는_DIALING을_IN_PROGRESS로_전이하고_startedAt을_채운다() {
        Call call = dialingCall();
        when(callRepository.findById(CALL_ID)).thenReturn(Optional.of(call));

        callService.connect(CALL_ID);

        assertThat(call.getStatus()).isEqualTo(CallStatus.IN_PROGRESS);
        assertThat(call.getStartedAt()).isNotNull();
    }

    @Test
    void finish는_연결된_통화를_COMPLETED로_마감한다() {
        Call call = dialingCall();
        call.connect(); // IN_PROGRESS
        when(callRepository.findById(CALL_ID)).thenReturn(Optional.of(call));

        callService.finish(CALL_ID);

        assertThat(call.getStatus()).isEqualTo(CallStatus.COMPLETED);
        assertThat(call.getEndedAt()).isNotNull();
        assertThat(call.getCallTime()).isNotNull().isGreaterThanOrEqualTo(0);
    }

    @Test
    void finish는_연결_전_통화를_CANCELED로_마감한다() {
        Call call = dialingCall(); // DIALING (connect 전)
        when(callRepository.findById(CALL_ID)).thenReturn(Optional.of(call));

        callService.finish(CALL_ID);

        assertThat(call.getStatus()).isEqualTo(CallStatus.CANCELED);
    }

    @Test
    void finish는_이미_종료된_통화엔_아무것도_하지_않는다() {
        Call call = dialingCall();
        call.connect();
        call.complete(); // COMPLETED
        when(callRepository.findById(CALL_ID)).thenReturn(Optional.of(call));

        // 정리 경로가 겹쳐 두 번 불려도 안전해야 한다(no-op).
        callService.finish(CALL_ID);

        assertThat(call.getStatus()).isEqualTo(CallStatus.COMPLETED);
    }

    @Test
    void connect는_통화가_없으면_CALL_NOT_FOUND를_던진다() {
        when(callRepository.findById(CALL_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> callService.connect(CALL_ID))
                .isInstanceOf(CallException.class)
                .extracting(e -> ((CallException) e).getErrorCode())
                .isEqualTo(CallErrorCode.CALL_NOT_FOUND);
    }

    @Test
    void finish는_통화가_없으면_CALL_NOT_FOUND를_던진다() {
        when(callRepository.findById(CALL_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> callService.finish(CALL_ID))
                .isInstanceOf(CallException.class)
                .extracting(e -> ((CallException) e).getErrorCode())
                .isEqualTo(CallErrorCode.CALL_NOT_FOUND);
    }
}
