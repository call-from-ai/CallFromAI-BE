package com.example.umcCall.domain.call.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.umcCall.domain.call.entity.Call;
import com.example.umcCall.domain.call.entity.CallReservation;
import com.example.umcCall.domain.call.enums.CallReservationStatus;
import com.example.umcCall.domain.call.enums.CallSender;
import com.example.umcCall.domain.call.enums.CallStatus;
import com.example.umcCall.domain.call.repository.CallRepository;
import com.example.umcCall.domain.call.repository.CallReservationRepository;
import com.example.umcCall.domain.character.entity.Character;
import com.example.umcCall.domain.relationship.entity.Relationship;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 예약 발신 처리 검증. 예약 전이는 실제 {@link CallReservation}으로 확인한다. */
@ExtendWith(MockitoExtension.class)
class CallReservationServiceTest {

    private static final Long RESERVATION_ID = 1L;
    private static final Long RELATIONSHIP_ID = 30L;

    @Mock private CallReservationRepository reservationRepository;
    @Mock private CallRepository callRepository;

    @InjectMocks private CallReservationService callReservationService;

    private Relationship callableRelationship() {
        Character character = mock(Character.class);
        lenient().when(character.getDeletedAt()).thenReturn(null);
        Relationship relationship = mock(Relationship.class);
        lenient().when(relationship.getId()).thenReturn(RELATIONSHIP_ID);
        lenient().when(relationship.isMain()).thenReturn(true);
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
        // 벨이 울리는 중(RINGING)이나 수락 후 연결 전(PENDING)에 또 발신하면 안 된다.
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
