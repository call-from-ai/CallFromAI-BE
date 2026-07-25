package com.example.umcCall.domain.call.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.umcCall.domain.call.dto.response.CallTicketResponse;
import com.example.umcCall.domain.call.entity.Call;
import com.example.umcCall.domain.call.enums.CallSender;
import com.example.umcCall.domain.call.enums.CallStatus;
import com.example.umcCall.domain.call.exception.CallErrorCode;
import com.example.umcCall.domain.call.exception.CallException;
import com.example.umcCall.domain.call.repository.CallRepository;
import com.example.umcCall.domain.call.ticket.WsTicket;
import com.example.umcCall.domain.call.ticket.WsTicketStore;
import com.example.umcCall.domain.character.entity.Character;
import com.example.umcCall.domain.relationship.entity.Relationship;
import com.example.umcCall.domain.relationship.repository.RelationshipRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 통화 상태 전이 위임(connect/finish) 검증. 엔티티 전이는 실제 {@link Call}로 확인한다. */
@ExtendWith(MockitoExtension.class)
class CallServiceTest {

    private static final Long CALL_ID = 1L;
    private static final Long MEMBER_ID = 10L;
    private static final Long OTHER_MEMBER_ID = 11L;
    private static final Long CHARACTER_ID = 20L;
    private static final Long RELATIONSHIP_ID = 30L;
    private static final String TICKET = "ticket-1";

    @Mock private RelationshipRepository relationshipRepository;
    @Mock private CallRepository callRepository;
    @Mock private WsTicketStore wsTicketStore;

    @InjectMocks private CallService callService;

    /** 사용자 발신 초기 상태 = DIALING. */
    private Call dialingCall() {
        return Call.builder().sender(CallSender.USER).build();
    }

    /** AI 발신 초기 상태 = RINGING. */
    private Call ringingCall(Relationship relationship) {
        return Call.builder().relationship(relationship).sender(CallSender.AI).build();
    }

    private Relationship relationshipOf(Long memberId) {
        Relationship relationship = mock(Relationship.class);
        lenient().when(relationship.getId()).thenReturn(RELATIONSHIP_ID);
        lenient().when(relationship.getMemberId()).thenReturn(memberId);
        return relationship;
    }

    private Character characterOf(Long characterId) {
        Character character = mock(Character.class);
        lenient().when(character.getId()).thenReturn(characterId);
        return character;
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
    void accept는_RINGING_통화에_티켓을_발급하고_PENDING으로_전이한다() {
        Relationship relationship = relationshipOf(MEMBER_ID);
        Character character = characterOf(CHARACTER_ID); // 중첩 스터빙 금지 — 먼저 만들고 주입한다
        when(relationship.getCharacter()).thenReturn(character);
        Call call = ringingCall(relationship);
        when(callRepository.findById(CALL_ID)).thenReturn(Optional.of(call));
        when(wsTicketStore.issue(any())).thenReturn(TICKET);

        CallTicketResponse response = callService.accept(MEMBER_ID, CALL_ID);

        assertThat(response.wsTicket()).isEqualTo(TICKET);
        // 수락은 PENDING까지만 — IN_PROGRESS는 WS가 열릴 때 connect()가 만든다.
        assertThat(response.callStatus()).isEqualTo(CallStatus.PENDING);
        assertThat(call.getStatus()).isEqualTo(CallStatus.PENDING);
        assertThat(call.getStartedAt()).isNull();

        ArgumentCaptor<WsTicket> captor = ArgumentCaptor.forClass(WsTicket.class);
        verify(wsTicketStore).issue(captor.capture());
        assertThat(captor.getValue().characterId()).isEqualTo(CHARACTER_ID);
    }

    @Test
    void accept는_남의_통화면_CALL_ACCESS_DENIED를_던진다() {
        Call call = ringingCall(relationshipOf(OTHER_MEMBER_ID));
        when(callRepository.findById(CALL_ID)).thenReturn(Optional.of(call));

        assertThatThrownBy(() -> callService.accept(MEMBER_ID, CALL_ID))
                .isInstanceOf(CallException.class)
                .extracting(e -> ((CallException) e).getErrorCode())
                .isEqualTo(CallErrorCode.CALL_ACCESS_DENIED);
    }

    @Test
    void accept는_착신_대기가_아니면_CALL_NOT_RINGING을_던진다() {
        Call call = ringingCall(relationshipOf(MEMBER_ID));
        call.accept();
        call.connect(); // IN_PROGRESS — 이미 통화 중
        when(callRepository.findById(CALL_ID)).thenReturn(Optional.of(call));

        assertThatThrownBy(() -> callService.accept(MEMBER_ID, CALL_ID))
                .isInstanceOf(CallException.class)
                .extracting(e -> ((CallException) e).getErrorCode())
                .isEqualTo(CallErrorCode.CALL_NOT_RINGING);
    }

    @Test
    void accept한_통화는_소켓이_안_열린_채_끝나면_CANCELED로_마감된다() {
        Relationship relationship = relationshipOf(MEMBER_ID);
        Character character = characterOf(CHARACTER_ID);
        when(relationship.getCharacter()).thenReturn(character);
        Call call = ringingCall(relationship);
        when(callRepository.findById(CALL_ID)).thenReturn(Optional.of(call));
        callService.accept(MEMBER_ID, CALL_ID); // PENDING

        // 스트림 개설 실패 등 서버 측 사유 — 사용자는 받았으므로 부재중이 아니다.
        callService.finish(CALL_ID);

        assertThat(call.getStatus()).isEqualTo(CallStatus.CANCELED);
    }

    @Test
    void reject는_RINGING_통화를_REJECTED로_마감하고_티켓을_발급하지_않는다() {
        Call call = ringingCall(relationshipOf(MEMBER_ID));
        when(callRepository.findById(CALL_ID)).thenReturn(Optional.of(call));

        callService.reject(MEMBER_ID, CALL_ID);

        assertThat(call.getStatus()).isEqualTo(CallStatus.REJECTED);
        verifyNoInteractions(wsTicketStore);
    }

    @Test
    void reject는_남의_통화면_CALL_ACCESS_DENIED를_던진다() {
        Call call = ringingCall(relationshipOf(OTHER_MEMBER_ID));
        when(callRepository.findById(CALL_ID)).thenReturn(Optional.of(call));

        assertThatThrownBy(() -> callService.reject(MEMBER_ID, CALL_ID))
                .isInstanceOf(CallException.class)
                .extracting(e -> ((CallException) e).getErrorCode())
                .isEqualTo(CallErrorCode.CALL_ACCESS_DENIED);
    }

    @Test
    void reject는_이미_받은_통화를_거절하지_못한다() {
        Call call = ringingCall(relationshipOf(MEMBER_ID));
        call.accept(); // PENDING — 받은 뒤 끊는 것은 소켓 경로의 CANCELED다
        when(callRepository.findById(CALL_ID)).thenReturn(Optional.of(call));

        assertThatThrownBy(() -> callService.reject(MEMBER_ID, CALL_ID))
                .isInstanceOf(CallException.class)
                .extracting(e -> ((CallException) e).getErrorCode())
                .isEqualTo(CallErrorCode.CALL_NOT_RINGING);
    }

    @Test
    void reject는_통화가_없으면_CALL_NOT_FOUND를_던진다() {
        when(callRepository.findById(CALL_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> callService.reject(MEMBER_ID, CALL_ID))
                .isInstanceOf(CallException.class)
                .extracting(e -> ((CallException) e).getErrorCode())
                .isEqualTo(CallErrorCode.CALL_NOT_FOUND);
    }

    @Test
    void accept는_통화가_없으면_CALL_NOT_FOUND를_던진다() {
        when(callRepository.findById(CALL_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> callService.accept(MEMBER_ID, CALL_ID))
                .isInstanceOf(CallException.class)
                .extracting(e -> ((CallException) e).getErrorCode())
                .isEqualTo(CallErrorCode.CALL_NOT_FOUND);
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
