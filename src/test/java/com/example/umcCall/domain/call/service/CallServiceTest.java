package com.example.umcCall.domain.call.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.umcCall.domain.call.dto.response.CallEndResponse;
import com.example.umcCall.domain.call.dto.response.CallIncomingResponse;
import com.example.umcCall.domain.call.dto.response.CallTicketResponse;
import com.example.umcCall.domain.call.entity.Call;
import com.example.umcCall.domain.call.enums.CallSender;
import com.example.umcCall.domain.call.enums.CallStatus;
import com.example.umcCall.domain.call.enums.CallEndReason;
import com.example.umcCall.domain.call.event.CallEndedEvent;
import com.example.umcCall.domain.call.exception.CallErrorCode;
import com.example.umcCall.domain.call.exception.CallException;
import com.example.umcCall.domain.call.repository.CallRepository;
import com.example.umcCall.domain.call.ticket.WsTicket;
import com.example.umcCall.domain.call.ticket.WsTicketStore;
import com.example.umcCall.domain.character.entity.Character;
import com.example.umcCall.domain.relationship.entity.Relationship;
import com.example.umcCall.domain.relationship.entity.RelationshipStatus;
import com.example.umcCall.domain.relationship.repository.RelationshipRepository;
import com.example.umcCall.domain.relationship.repository.RelationshipStatusRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;

/** 착신 응답(accept/reject)·종료·스위퍼 마감과 상태 전이 위임 검증. 전이는 실제 {@link Call}로 확인한다. */
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
    @Mock private RelationshipStatusRepository relationshipStatusRepository;
    @Mock private WsTicketStore wsTicketStore;
    @Mock private ApplicationEventPublisher eventPublisher;

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
        when(callRepository.findByIdForUpdate(CALL_ID)).thenReturn(Optional.of(call));

        callService.connect(CALL_ID);

        assertThat(call.getStatus()).isEqualTo(CallStatus.IN_PROGRESS);
        assertThat(call.getStartedAt()).isNotNull();
    }

    @Test
    void finish는_연결된_통화를_COMPLETED로_마감한다() {
        Relationship relationship = relationshipOf(MEMBER_ID);
        Call call = Call.builder().relationship(relationship).sender(CallSender.USER).build();
        call.connect(); // IN_PROGRESS
        when(callRepository.findByIdForUpdate(CALL_ID)).thenReturn(Optional.of(call));
        RelationshipStatus status = RelationshipStatus.builder().relationship(relationship).build();
        when(relationshipStatusRepository.findByRelationshipId(RELATIONSHIP_ID)).thenReturn(Optional.of(status));

        callService.finish(CALL_ID);

        assertThat(call.getStatus()).isEqualTo(CallStatus.COMPLETED);
        assertThat(call.getEndedAt()).isNotNull();
        assertThat(call.getCallTime()).isNotNull().isGreaterThanOrEqualTo(0);
        assertThat(status.getTotalCallCount()).isEqualTo(1);
        assertThat(status.getCallStreakDays()).isEqualTo(1);
    }

    @Test
    void finish는_연결_전_통화를_CANCELED로_마감한다() {
        Call call = dialingCall(); // DIALING (connect 전)
        when(callRepository.findByIdForUpdate(CALL_ID)).thenReturn(Optional.of(call));

        callService.finish(CALL_ID);

        assertThat(call.getStatus()).isEqualTo(CallStatus.CANCELED);
    }

    @Test
    void finish는_이미_종료된_통화엔_아무것도_하지_않는다() {
        Call call = dialingCall();
        call.connect();
        call.complete(); // COMPLETED
        when(callRepository.findByIdForUpdate(CALL_ID)).thenReturn(Optional.of(call));

        // 정리 경로가 겹쳐 두 번 불려도 안전해야 한다(no-op).
        callService.finish(CALL_ID);

        assertThat(call.getStatus()).isEqualTo(CallStatus.COMPLETED);
    }

    @Test
    void closeOverrunCall은_진행_중인_통화를_COMPLETED로_마감하고_세션_정리를_알린다() {
        Relationship relationship = relationshipOf(MEMBER_ID);
        Call call = Call.builder().relationship(relationship).sender(CallSender.USER).build();
        call.connect(); // IN_PROGRESS
        when(callRepository.findByIdForUpdate(CALL_ID)).thenReturn(Optional.of(call));
        when(relationshipStatusRepository.findByRelationshipId(RELATIONSHIP_ID))
                .thenReturn(Optional.of(RelationshipStatus.builder().relationship(relationship).build()));

        callService.closeOverrunCall(CALL_ID);

        assertThat(call.getStatus()).isEqualTo(CallStatus.COMPLETED);
        // 상태만 바꾸면 소켓이 살아남아 오디오가 계속 CLOVA로 흐른다.
        // 사유는 TIMEOUT — 운영 안전망이지 AI가 끊은 게 아니다(프론트 문구가 갈린다).
        verify(eventPublisher).publishEvent(
                new CallEndedEvent(CALL_ID, CallEndReason.TIMEOUT, call.getCallTime()));
    }

    @Test
    void closeOverrunCall은_진행_중이_아니면_아무것도_하지_않는다() {
        Call call = dialingCall(); // DIALING — 연결 대기 스위퍼의 몫이다
        when(callRepository.findByIdForUpdate(CALL_ID)).thenReturn(Optional.of(call));

        callService.closeOverrunCall(CALL_ID);

        assertThat(call.getStatus()).isEqualTo(CallStatus.DIALING);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void getIncomingCall은_RINGING_통화를_단건으로_준다() {
        CallIncomingResponse incoming = new CallIncomingResponse(
                CALL_ID, CHARACTER_ID, "지호", "https://cdn.example.com/jiho.png", LocalDateTime.now());
        when(callRepository.findIncomingCalls(eq(MEMBER_ID), eq(CallStatus.RINGING), any(Pageable.class)))
                .thenReturn(List.of(incoming));

        assertThat(callService.getIncomingCall(MEMBER_ID)).isEqualTo(incoming);
    }

    @Test
    void getIncomingCall은_착신이_없으면_null을_준다() {
        when(callRepository.findIncomingCalls(eq(MEMBER_ID), eq(CallStatus.RINGING), any(Pageable.class)))
                .thenReturn(List.of());

        assertThat(callService.getIncomingCall(MEMBER_ID)).isNull();
    }

    @Test
    void getIncomingCall은_착신이_겹치면_가장_최근_것을_준다() {
        CallIncomingResponse latest = new CallIncomingResponse(
                CALL_ID, CHARACTER_ID, "지호", null, LocalDateTime.now());
        CallIncomingResponse older = new CallIncomingResponse(
                99L, 98L, "이전메인", null, LocalDateTime.now().minusMinutes(1));
        // 최신순 조회라 첫 건이 최신이다(메인 캐릭터 교체 엣지).
        when(callRepository.findIncomingCalls(eq(MEMBER_ID), eq(CallStatus.RINGING), any(Pageable.class)))
                .thenReturn(List.of(latest, older));

        assertThat(callService.getIncomingCall(MEMBER_ID)).isEqualTo(latest);
    }

    /** 발신 가능한 관계(살아 있는 메인 캐릭터)를 세운다. */
    private Relationship givenDialableRelationship() {
        Relationship relationship = relationshipOf(MEMBER_ID);
        Character character = characterOf(CHARACTER_ID); // when() 인자 안에서 스터빙하지 않는다
        lenient().when(relationship.isMain()).thenReturn(true);
        lenient().when(relationship.getCharacter()).thenReturn(character);
        when(relationshipRepository.findByCharacterIdAndCharacterDeletedAtIsNull(CHARACTER_ID))
                .thenReturn(Optional.of(relationship));
        return relationship;
    }

    private void givenActiveCalls(List<Call> activeCalls) {
        when(callRepository.findActiveByRelationshipIdForUpdate(RELATIONSHIP_ID, CallStatus.ACTIVE))
                .thenReturn(activeCalls);
    }

    @Test
    void dial은_관계_락을_잡고_통화와_티켓을_만든다() {
        givenDialableRelationship();
        givenActiveCalls(List.of());
        when(callRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(wsTicketStore.issue(any())).thenReturn(TICKET);

        CallTicketResponse response = callService.dial(MEMBER_ID, CHARACTER_ID);

        assertThat(response.callStatus()).isEqualTo(CallStatus.DIALING);
        assertThat(response.wsTicket()).isEqualTo(TICKET);
        // 락이 없으면 예약 발신(fire)과 겹칠 때 같은 관계에 통화가 둘 생긴다.
        verify(relationshipRepository).findByIdForUpdate(RELATIONSHIP_ID);
    }

    @Test
    void dial은_소켓을_못_연_자기_발신을_취소하고_새로_만든다() {
        givenDialableRelationship();
        Call stale = dialingCall(); // 이전 시도 — 티켓이 1회용이라 물려줄 수 없다
        givenActiveCalls(List.of(stale));
        when(callRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(wsTicketStore.issue(any())).thenReturn(TICKET);

        CallTicketResponse response = callService.dial(MEMBER_ID, CHARACTER_ID);

        assertThat(stale.getStatus()).isEqualTo(CallStatus.CANCELED);
        assertThat(response.callStatus()).isEqualTo(CallStatus.DIALING);
    }

    @Test
    void dial은_AI가_거는_중이면_CALL_ALREADY_ACTIVE를_던진다() {
        Relationship relationship = givenDialableRelationship();
        Call incoming = ringingCall(relationship);
        givenActiveCalls(List.of(incoming));

        assertThatThrownBy(() -> callService.dial(MEMBER_ID, CHARACTER_ID))
                .isInstanceOf(CallException.class)
                .extracting(e -> ((CallException) e).getErrorCode())
                .isEqualTo(CallErrorCode.CALL_ALREADY_ACTIVE);

        // 서버가 상대 통화를 임의로 끊지 않는다 — 사용자는 걸 게 아니라 받으면 된다.
        assertThat(incoming.getStatus()).isEqualTo(CallStatus.RINGING);
        verify(callRepository, never()).save(any());
    }

    @Test
    void dial은_이미_통화_중이면_CALL_ALREADY_ACTIVE를_던진다() {
        Relationship relationship = givenDialableRelationship();
        Call ongoing = ringingCall(relationship);
        ongoing.accept();
        ongoing.connect(); // IN_PROGRESS
        givenActiveCalls(List.of(ongoing));

        assertThatThrownBy(() -> callService.dial(MEMBER_ID, CHARACTER_ID))
                .isInstanceOf(CallException.class)
                .extracting(e -> ((CallException) e).getErrorCode())
                .isEqualTo(CallErrorCode.CALL_ALREADY_ACTIVE);
    }

    @Test
    void accept는_RINGING_통화에_티켓을_발급하고_PENDING으로_전이한다() {
        Relationship relationship = relationshipOf(MEMBER_ID);
        Character character = characterOf(CHARACTER_ID);
        when(relationship.getCharacter()).thenReturn(character);
        Call call = ringingCall(relationship);
        when(callRepository.findByIdForUpdate(CALL_ID)).thenReturn(Optional.of(call));
        when(wsTicketStore.issue(any())).thenReturn(TICKET);

        CallTicketResponse response = callService.accept(MEMBER_ID, CALL_ID);

        assertThat(response.wsTicket()).isEqualTo(TICKET);
        // 수락은 PENDING까지만 — IN_PROGRESS는 connect()가 만든다.
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
        when(callRepository.findByIdForUpdate(CALL_ID)).thenReturn(Optional.of(call));

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
        when(callRepository.findByIdForUpdate(CALL_ID)).thenReturn(Optional.of(call));

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
        when(callRepository.findByIdForUpdate(CALL_ID)).thenReturn(Optional.of(call));
        callService.accept(MEMBER_ID, CALL_ID); // PENDING

        // 서버 측 사유(스트림 개설 실패 등) — 사용자는 받았으므로 부재중이 아니다.
        callService.finish(CALL_ID);

        assertThat(call.getStatus()).isEqualTo(CallStatus.CANCELED);
    }

    @Test
    void reject는_RINGING_통화를_REJECTED로_마감하고_티켓을_발급하지_않는다() {
        Call call = ringingCall(relationshipOf(MEMBER_ID));
        when(callRepository.findByIdForUpdate(CALL_ID)).thenReturn(Optional.of(call));

        callService.reject(MEMBER_ID, CALL_ID);

        assertThat(call.getStatus()).isEqualTo(CallStatus.REJECTED);
        verifyNoInteractions(wsTicketStore);
    }

    @Test
    void reject는_남의_통화면_CALL_ACCESS_DENIED를_던진다() {
        Call call = ringingCall(relationshipOf(OTHER_MEMBER_ID));
        when(callRepository.findByIdForUpdate(CALL_ID)).thenReturn(Optional.of(call));

        assertThatThrownBy(() -> callService.reject(MEMBER_ID, CALL_ID))
                .isInstanceOf(CallException.class)
                .extracting(e -> ((CallException) e).getErrorCode())
                .isEqualTo(CallErrorCode.CALL_ACCESS_DENIED);
    }

    @Test
    void reject는_이미_받은_통화를_거절하지_못한다() {
        Call call = ringingCall(relationshipOf(MEMBER_ID));
        call.accept(); // 받은 뒤 끊는 것은 소켓 경로의 CANCELED다
        when(callRepository.findByIdForUpdate(CALL_ID)).thenReturn(Optional.of(call));

        assertThatThrownBy(() -> callService.reject(MEMBER_ID, CALL_ID))
                .isInstanceOf(CallException.class)
                .extracting(e -> ((CallException) e).getErrorCode())
                .isEqualTo(CallErrorCode.CALL_NOT_RINGING);
    }

    @Test
    void reject는_통화가_없으면_CALL_NOT_FOUND를_던진다() {
        when(callRepository.findByIdForUpdate(CALL_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> callService.reject(MEMBER_ID, CALL_ID))
                .isInstanceOf(CallException.class)
                .extracting(e -> ((CallException) e).getErrorCode())
                .isEqualTo(CallErrorCode.CALL_NOT_FOUND);
    }

    @Test
    void accept는_통화가_없으면_CALL_NOT_FOUND를_던진다() {
        when(callRepository.findByIdForUpdate(CALL_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> callService.accept(MEMBER_ID, CALL_ID))
                .isInstanceOf(CallException.class)
                .extracting(e -> ((CallException) e).getErrorCode())
                .isEqualTo(CallErrorCode.CALL_NOT_FOUND);
    }

    @Test
    void end는_진행_중_통화를_COMPLETED로_마감하고_통화시간을_준다() {
        Relationship relationship = relationshipOf(MEMBER_ID);
        Call call = ringingCall(relationship);
        call.accept();
        call.connect(); // IN_PROGRESS
        when(callRepository.findByIdForUpdate(CALL_ID)).thenReturn(Optional.of(call));
        when(relationshipStatusRepository.findByRelationshipId(RELATIONSHIP_ID))
                .thenReturn(Optional.of(RelationshipStatus.builder().relationship(relationship).build()));

        CallEndResponse response = callService.end(MEMBER_ID, CALL_ID);

        assertThat(call.getStatus()).isEqualTo(CallStatus.COMPLETED);
        // 종료 화면이 쓰는 값 — 서버가 startedAt~endedAt으로 계산한다.
        assertThat(response.callTime()).isNotNull().isGreaterThanOrEqualTo(0);
        assertThat(response.endedAt()).isNotNull();
    }

    @Test
    void end는_세션_정리를_위해_종료_이벤트를_발행한다() {
        Relationship relationship = relationshipOf(MEMBER_ID);
        Call call = ringingCall(relationship);
        call.accept();
        call.connect();
        when(callRepository.findByIdForUpdate(CALL_ID)).thenReturn(Optional.of(call));
        when(relationshipStatusRepository.findByRelationshipId(RELATIONSHIP_ID))
                .thenReturn(Optional.of(RelationshipStatus.builder().relationship(relationship).build()));

        callService.end(MEMBER_ID, CALL_ID);

        // 소켓을 안 닫으면 오디오가 계속 CLOVA로 흘러 STT 비용이 발생한다.
        // callTime은 REST 응답과 같은 값이어야 한다 — 프론트가 두 경로에서 다른 통화 시간을 보면 안 된다.
        verify(eventPublisher).publishEvent(
                new CallEndedEvent(CALL_ID, CallEndReason.USER_ENDED, call.getCallTime()));
    }

    @Test
    void end는_남의_통화면_CALL_ACCESS_DENIED를_던진다() {
        Call call = ringingCall(relationshipOf(OTHER_MEMBER_ID));
        call.accept();
        call.connect();
        when(callRepository.findByIdForUpdate(CALL_ID)).thenReturn(Optional.of(call));

        assertThatThrownBy(() -> callService.end(MEMBER_ID, CALL_ID))
                .isInstanceOf(CallException.class)
                .extracting(e -> ((CallException) e).getErrorCode())
                .isEqualTo(CallErrorCode.CALL_ACCESS_DENIED);
    }

    @Test
    void end는_연결_전_통화면_CALL_NOT_IN_PROGRESS를_던진다() {
        Call call = ringingCall(relationshipOf(MEMBER_ID));
        call.accept(); // 아직 오디오가 흐르지 않는다
        when(callRepository.findByIdForUpdate(CALL_ID)).thenReturn(Optional.of(call));

        assertThatThrownBy(() -> callService.end(MEMBER_ID, CALL_ID))
                .isInstanceOf(CallException.class)
                .extracting(e -> ((CallException) e).getErrorCode())
                .isEqualTo(CallErrorCode.CALL_NOT_IN_PROGRESS);
    }

    @Test
    void end는_통화가_없으면_CALL_NOT_FOUND를_던진다() {
        when(callRepository.findByIdForUpdate(CALL_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> callService.end(MEMBER_ID, CALL_ID))
                .isInstanceOf(CallException.class)
                .extracting(e -> ((CallException) e).getErrorCode())
                .isEqualTo(CallErrorCode.CALL_NOT_FOUND);
    }

    @Test
    void markMissed는_RINGING_통화를_MISSED로_마감한다() {
        Call call = ringingCall(relationshipOf(MEMBER_ID));
        when(callRepository.findByIdForUpdate(CALL_ID)).thenReturn(Optional.of(call));

        callService.markMissed(CALL_ID);

        assertThat(call.getStatus()).isEqualTo(CallStatus.MISSED);
    }

    @Test
    void markMissed는_그_사이_사용자가_받았으면_마감하지_않는다() {
        Call call = ringingCall(relationshipOf(MEMBER_ID));
        call.accept(); // 스위퍼가 조회한 뒤 사용자가 받은 상황
        when(callRepository.findByIdForUpdate(CALL_ID)).thenReturn(Optional.of(call));

        callService.markMissed(CALL_ID);

        // 락 뒤 상태 재확인이 "받는 순간 부재중 처리" 레이스를 막는다.
        assertThat(call.getStatus()).isEqualTo(CallStatus.PENDING);
    }

    @Test
    void cancelStalePending은_PENDING_통화를_CANCELED로_마감한다() {
        Call call = ringingCall(relationshipOf(MEMBER_ID));
        call.accept(); // PENDING
        when(callRepository.findByIdForUpdate(CALL_ID)).thenReturn(Optional.of(call));

        callService.cancelStalePending(CALL_ID);

        assertThat(call.getStatus()).isEqualTo(CallStatus.CANCELED);
    }

    @Test
    void cancelStalePending은_그_사이_연결된_통화를_마감하지_않는다() {
        Call call = ringingCall(relationshipOf(MEMBER_ID));
        call.accept();
        call.connect(); // IN_PROGRESS — 막 접속 성공
        when(callRepository.findByIdForUpdate(CALL_ID)).thenReturn(Optional.of(call));

        callService.cancelStalePending(CALL_ID);

        assertThat(call.getStatus()).isEqualTo(CallStatus.IN_PROGRESS);
    }

    @Test
    void 스위퍼_전이는_통화가_없으면_아무것도_하지_않는다() {
        when(callRepository.findByIdForUpdate(CALL_ID)).thenReturn(Optional.empty());

        callService.markMissed(CALL_ID);
        callService.cancelStalePending(CALL_ID);
    }

    @Test
    void connect는_통화가_없으면_CALL_NOT_FOUND를_던진다() {
        when(callRepository.findByIdForUpdate(CALL_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> callService.connect(CALL_ID))
                .isInstanceOf(CallException.class)
                .extracting(e -> ((CallException) e).getErrorCode())
                .isEqualTo(CallErrorCode.CALL_NOT_FOUND);
    }

    @Test
    void finish는_통화가_없으면_CALL_NOT_FOUND를_던진다() {
        when(callRepository.findByIdForUpdate(CALL_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> callService.finish(CALL_ID))
                .isInstanceOf(CallException.class)
                .extracting(e -> ((CallException) e).getErrorCode())
                .isEqualTo(CallErrorCode.CALL_NOT_FOUND);
    }
}
