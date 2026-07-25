package com.example.umcCall.domain.call.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.umcCall.domain.call.dto.response.CallReservationItem;
import com.example.umcCall.domain.call.entity.Call;
import com.example.umcCall.domain.call.entity.CallReservation;
import com.example.umcCall.domain.call.enums.CallReservationStatus;
import com.example.umcCall.domain.call.enums.CallSender;
import com.example.umcCall.domain.call.enums.CallStatus;
import com.example.umcCall.domain.call.exception.CallErrorCode;
import com.example.umcCall.domain.call.exception.CallException;
import com.example.umcCall.domain.call.repository.CallRepository;
import com.example.umcCall.domain.call.repository.CallReservationRepository;
import com.example.umcCall.domain.character.entity.Character;
import com.example.umcCall.domain.relationship.entity.Relationship;
import com.example.umcCall.domain.relationship.repository.RelationshipRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 예약 조회·수정·발신 검증. 예약 전이는 실제 {@link CallReservation}으로 확인한다. */
@ExtendWith(MockitoExtension.class)
class CallReservationServiceTest {

    private static final Long RESERVATION_ID = 1L;
    private static final Long RELATIONSHIP_ID = 30L;
    private static final Long MEMBER_ID = 10L;
    private static final Long OTHER_MEMBER_ID = 11L;

    @Mock private CallReservationRepository reservationRepository;
    @Mock private CallRepository callRepository;
    @Mock private RelationshipRepository relationshipRepository;

    @InjectMocks private CallReservationService callReservationService;

    private Relationship callableRelationship() {
        Character character = mock(Character.class);
        lenient().when(character.getDeletedAt()).thenReturn(null);
        Relationship relationship = mock(Relationship.class);
        lenient().when(relationship.getId()).thenReturn(RELATIONSHIP_ID);
        lenient().when(relationship.isMain()).thenReturn(true);
        lenient().when(relationship.getMemberId()).thenReturn(MEMBER_ID);
        lenient().when(relationship.getCharacter()).thenReturn(character);
        return relationship;
    }

    private CallReservation reservationOf(Relationship relationship) {
        return CallReservation.builder()
                .relationship(relationship)
                .scheduledAt(LocalDateTime.now())
                .build();
    }

    private void givenClaimed(CallReservation reservation) {
        when(reservationRepository.findByIdForUpdate(RESERVATION_ID)).thenReturn(Optional.of(reservation));
    }

    private void givenNoActiveCall() {
        when(callRepository.existsByRelationshipIdAndStatusIn(anyLong(), any())).thenReturn(false);
    }

    private void givenRelationship(Relationship relationship) {
        when(relationshipRepository.findById(RELATIONSHIP_ID)).thenReturn(Optional.of(relationship));
    }

    @Test
    void reserve는_대기_중_예약을_만든다() {
        givenRelationship(callableRelationship());
        when(reservationRepository.existsByRelationshipIdAndStatus(
                RELATIONSHIP_ID, CallReservationStatus.SCHEDULED)).thenReturn(false);
        when(reservationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        LocalDateTime scheduledAt = LocalDateTime.now().plusHours(3);

        callReservationService.reserve(RELATIONSHIP_ID, scheduledAt);

        ArgumentCaptor<CallReservation> captor = ArgumentCaptor.forClass(CallReservation.class);
        verify(reservationRepository).save(captor.capture());
        CallReservation created = captor.getValue();
        assertThat(created.getScheduledAt()).isEqualTo(scheduledAt);
        assertThat(created.getStatus()).isEqualTo(CallReservationStatus.SCHEDULED);
    }

    @Test
    void reserve는_관계가_없으면_TARGET_NOT_FOUND를_던진다() {
        when(relationshipRepository.findById(RELATIONSHIP_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                callReservationService.reserve(RELATIONSHIP_ID, LocalDateTime.now().plusHours(3)))
                .isInstanceOf(CallException.class)
                .extracting(e -> ((CallException) e).getErrorCode())
                .isEqualTo(CallErrorCode.CALL_TARGET_NOT_FOUND);
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void reserve는_캐릭터가_삭제된_관계면_TARGET_NOT_FOUND를_던진다() {
        Character deleted = mock(Character.class);
        when(deleted.getDeletedAt()).thenReturn(LocalDateTime.now());
        Relationship relationship = callableRelationship();
        when(relationship.getCharacter()).thenReturn(deleted);
        givenRelationship(relationship);

        assertThatThrownBy(() ->
                callReservationService.reserve(RELATIONSHIP_ID, LocalDateTime.now().plusHours(3)))
                .isInstanceOf(CallException.class)
                .extracting(e -> ((CallException) e).getErrorCode())
                .isEqualTo(CallErrorCode.CALL_TARGET_NOT_FOUND);
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void reserve는_메인이_아닌_관계면_TARGET_NOT_MAIN을_던진다() {
        Relationship relationship = callableRelationship();
        when(relationship.isMain()).thenReturn(false);
        givenRelationship(relationship);

        assertThatThrownBy(() ->
                callReservationService.reserve(RELATIONSHIP_ID, LocalDateTime.now().plusHours(3)))
                .isInstanceOf(CallException.class)
                .extracting(e -> ((CallException) e).getErrorCode())
                .isEqualTo(CallErrorCode.CALL_TARGET_NOT_MAIN);
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void reserve는_지난_시각이면_PAST_TIME을_던진다() {
        givenRelationship(callableRelationship());

        // 스케줄러가 grace window 밖으로 보고 곧 종결시킬 예약이라 애초에 받지 않는다.
        assertThatThrownBy(() ->
                callReservationService.reserve(RELATIONSHIP_ID, LocalDateTime.now().minusMinutes(1)))
                .isInstanceOf(CallException.class)
                .extracting(e -> ((CallException) e).getErrorCode())
                .isEqualTo(CallErrorCode.CALL_RESERVATION_PAST_TIME);
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void reserve는_같은_관계에_대기_중_예약이_있으면_ALREADY_EXISTS를_던진다() {
        givenRelationship(callableRelationship());
        when(reservationRepository.existsByRelationshipIdAndStatus(
                RELATIONSHIP_ID, CallReservationStatus.SCHEDULED)).thenReturn(true);

        assertThatThrownBy(() ->
                callReservationService.reserve(RELATIONSHIP_ID, LocalDateTime.now().plusHours(3)))
                .isInstanceOf(CallException.class)
                .extracting(e -> ((CallException) e).getErrorCode())
                .isEqualTo(CallErrorCode.CALL_RESERVATION_ALREADY_EXISTS);
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void getMyReservations는_대기_중_예약만_조회한다() {
        CallReservationItem item = new CallReservationItem(
                RESERVATION_ID, 20L, "지호", null, LocalDateTime.now().plusHours(2));
        when(reservationRepository.findMyReservations(
                eq(MEMBER_ID), eq(CallReservationStatus.SCHEDULED), any(), any()))
                .thenReturn(List.of(item));

        assertThat(callReservationService.getMyReservations(MEMBER_ID).content()).containsExactly(item);
    }

    @Test
    void getMyReservations의_조회_창은_당일_0시부터_다음날_새벽5시까지다() {
        when(reservationRepository.findMyReservations(any(), any(), any(), any())).thenReturn(List.of());

        callReservationService.getMyReservations(MEMBER_ID);

        ArgumentCaptor<LocalDateTime> from = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> to = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(reservationRepository).findMyReservations(any(), any(), from.capture(), to.capture());

        LocalDate today = LocalDate.now();
        assertThat(from.getValue()).isEqualTo(today.atStartOfDay());
        // 새벽 예약이 자정을 넘겼다고 사라지지 않게 다음날 05시까지 본다.
        assertThat(to.getValue()).isEqualTo(today.plusDays(1).atTime(5, 0));
    }

    @Test
    void reschedule은_대기_중_예약의_시각을_바꾼다() {
        CallReservation reservation = reservationOf(callableRelationship());
        givenClaimed(reservation);
        LocalDateTime newTime = LocalDateTime.now().plusDays(1);

        callReservationService.reschedule(MEMBER_ID, RESERVATION_ID, newTime);

        assertThat(reservation.getScheduledAt()).isEqualTo(newTime);
        assertThat(reservation.getStatus()).isEqualTo(CallReservationStatus.SCHEDULED);
    }

    @Test
    void reschedule은_남의_예약이면_ACCESS_DENIED를_던진다() {
        Relationship relationship = callableRelationship();
        when(relationship.getMemberId()).thenReturn(OTHER_MEMBER_ID);
        givenClaimed(reservationOf(relationship));

        assertThatThrownBy(() ->
                callReservationService.reschedule(MEMBER_ID, RESERVATION_ID, LocalDateTime.now().plusDays(1)))
                .isInstanceOf(CallException.class)
                .extracting(e -> ((CallException) e).getErrorCode())
                .isEqualTo(CallErrorCode.CALL_RESERVATION_ACCESS_DENIED);
    }

    @Test
    void reschedule은_이미_발신된_예약이면_NOT_SCHEDULED를_던진다() {
        CallReservation reservation = reservationOf(callableRelationship());
        reservation.markFired();
        givenClaimed(reservation);

        assertThatThrownBy(() ->
                callReservationService.reschedule(MEMBER_ID, RESERVATION_ID, LocalDateTime.now().plusDays(1)))
                .isInstanceOf(CallException.class)
                .extracting(e -> ((CallException) e).getErrorCode())
                .isEqualTo(CallErrorCode.CALL_RESERVATION_NOT_SCHEDULED);
    }

    @Test
    void reschedule은_예약이_없으면_NOT_FOUND를_던진다() {
        when(reservationRepository.findByIdForUpdate(RESERVATION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                callReservationService.reschedule(MEMBER_ID, RESERVATION_ID, LocalDateTime.now().plusDays(1)))
                .isInstanceOf(CallException.class)
                .extracting(e -> ((CallException) e).getErrorCode())
                .isEqualTo(CallErrorCode.CALL_RESERVATION_NOT_FOUND);
    }

    @Test
    void fire는_AI_발신_통화를_만들고_예약을_FIRED로_전이한다() {
        CallReservation reservation = reservationOf(callableRelationship());
        givenClaimed(reservation);
        givenNoActiveCall();
        when(callRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        callReservationService.fire(RESERVATION_ID);

        ArgumentCaptor<Call> captor = ArgumentCaptor.forClass(Call.class);
        verify(callRepository).save(captor.capture());
        Call created = captor.getValue();
        assertThat(created.getSender()).isEqualTo(CallSender.AI);
        assertThat(created.getStatus()).isEqualTo(CallStatus.RINGING);
        assertThat(reservation.getStatus()).isEqualTo(CallReservationStatus.FIRED);
    }

    @Test
    void fire는_이미_통화_중이면_발신하지_않고_예약을_CANCELED로_종결한다() {
        CallReservation reservation = reservationOf(callableRelationship());
        givenClaimed(reservation);
        when(callRepository.existsByRelationshipIdAndStatusIn(anyLong(), any())).thenReturn(true);

        callReservationService.fire(RESERVATION_ID);

        verify(callRepository, never()).save(any());
        assertThat(reservation.getStatus()).isEqualTo(CallReservationStatus.CANCELED);
    }

    @Test
    void 통화_중_판정에는_RINGING과_PENDING도_포함된다() {
        CallReservation reservation = reservationOf(callableRelationship());
        givenClaimed(reservation);
        givenNoActiveCall();
        when(callRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        callReservationService.fire(RESERVATION_ID);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<CallStatus>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(callRepository).existsByRelationshipIdAndStatusIn(eq(RELATIONSHIP_ID), captor.capture());
        // 벨이 울리는 중이나 수락 후 연결 전에 또 발신하면 안 된다.
        assertThat(captor.getValue()).containsExactlyInAnyOrder(
                CallStatus.DIALING, CallStatus.RINGING, CallStatus.PENDING, CallStatus.IN_PROGRESS);
    }

    @Test
    void fire는_메인이_아닌_관계면_발신하지_않고_예약을_CANCELED로_종결한다() {
        Relationship relationship = callableRelationship();
        when(relationship.isMain()).thenReturn(false);
        CallReservation reservation = reservationOf(relationship);
        givenClaimed(reservation);

        callReservationService.fire(RESERVATION_ID);

        verify(callRepository, never()).save(any());
        assertThat(reservation.getStatus()).isEqualTo(CallReservationStatus.CANCELED);
    }

    @Test
    void fire는_캐릭터가_삭제된_관계면_발신하지_않고_예약을_CANCELED로_종결한다() {
        Character deleted = mock(Character.class);
        when(deleted.getDeletedAt()).thenReturn(LocalDateTime.now());
        Relationship relationship = callableRelationship();
        when(relationship.getCharacter()).thenReturn(deleted);
        CallReservation reservation = reservationOf(relationship);
        givenClaimed(reservation);

        callReservationService.fire(RESERVATION_ID);

        verify(callRepository, never()).save(any());
        assertThat(reservation.getStatus()).isEqualTo(CallReservationStatus.CANCELED);
    }

    @Test
    void fire는_이미_처리된_예약을_두_번_발신하지_않는다() {
        CallReservation reservation = reservationOf(callableRelationship());
        reservation.markFired(); // 다른 인스턴스가 먼저 집은 상황
        givenClaimed(reservation);

        callReservationService.fire(RESERVATION_ID);

        verify(callRepository, never()).save(any());
        assertThat(reservation.getStatus()).isEqualTo(CallReservationStatus.FIRED);
    }

    @Test
    void fire는_예약이_없으면_아무것도_하지_않는다() {
        when(reservationRepository.findByIdForUpdate(RESERVATION_ID)).thenReturn(Optional.empty());

        callReservationService.fire(RESERVATION_ID);

        verify(callRepository, never()).save(any());
    }

    @Test
    void expire는_대기_중인_예약을_CANCELED로_종결한다() {
        CallReservation reservation = reservationOf(callableRelationship());
        givenClaimed(reservation);

        callReservationService.expire(RESERVATION_ID);

        assertThat(reservation.getStatus()).isEqualTo(CallReservationStatus.CANCELED);
        verify(callRepository, never()).save(any());
    }

    @Test
    void expire는_이미_발신한_예약을_건드리지_않는다() {
        CallReservation reservation = reservationOf(callableRelationship());
        reservation.markFired();
        givenClaimed(reservation);

        callReservationService.expire(RESERVATION_ID);

        assertThat(reservation.getStatus()).isEqualTo(CallReservationStatus.FIRED);
    }
}
