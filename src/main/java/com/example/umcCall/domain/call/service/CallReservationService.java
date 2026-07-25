package com.example.umcCall.domain.call.service;

import com.example.umcCall.domain.call.entity.Call;
import com.example.umcCall.domain.call.entity.CallReservation;
import com.example.umcCall.domain.call.enums.CallReservationStatus;
import com.example.umcCall.domain.call.enums.CallSender;
import com.example.umcCall.domain.call.enums.CallStatus;
import com.example.umcCall.domain.call.repository.CallRepository;
import com.example.umcCall.domain.call.repository.CallReservationRepository;
import com.example.umcCall.domain.relationship.entity.Relationship;
import java.util.EnumSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 예약 통화의 발신 처리. 스케줄러({@code CallReservationWorker})가 골라 온 예약 id를 하나씩 받아
 * AI 발신 {@link Call}로 옮긴다.
 *
 * <p>⚠ <b>예약 1건 = 트랜잭션 1개</b>다(배치 전체가 한 tx가 아니다). 한 예약이 실패해도 나머지가
 * 처리되고, 락을 오래 잡지 않는다. 루프는 워커가 트랜잭션 밖에서 돈다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class CallReservationService {

    /**
     * "이미 통화 중"으로 볼 상태. 착신 대기(RINGING)·수락 후 연결 전(PENDING)도 포함한다 —
     * 벨이 울리는 중에 또 벨을 울리면 안 된다. 부재중/미접속 스위퍼가 이 상태들을 유계로 유지한다.
     */
    private static final Set<CallStatus> ACTIVE_CALL_STATUSES =
            EnumSet.of(CallStatus.DIALING, CallStatus.RINGING,
                    CallStatus.PENDING, CallStatus.IN_PROGRESS);

    private final CallReservationRepository reservationRepository;
    private final CallRepository callRepository;

    /**
     * 예약 1건을 발신으로 옮긴다. 발신하면 예약은 FIRED, 발신 조건이 아니면 CANCELED로 종결한다.
     * <p>비관적 락으로 집은 뒤 <b>상태를 다시 확인</b>한다 — 다른 인스턴스나 중복 tick이 먼저 집었으면
     * 그쪽이 이미 FIRED로 바꿨으므로 조용히 반환한다(예약 하나는 한 번만 울린다).
     */
    public void fire(Long reservationId) {
        CallReservation reservation = reservationRepository.findByIdForUpdate(reservationId).orElse(null);
        if (reservation == null || reservation.getStatus() != CallReservationStatus.SCHEDULED) {
            return;
        }

        Relationship relationship = reservation.getRelationship();
        if (!isCallable(relationship)) {
            reservation.cancel();
            log.info("예약 발신 취소(관계가 통화 대상이 아님). reservationId={}, relationshipId={}",
                    reservationId, relationship.getId());
            return;
        }
        if (callRepository.existsByRelationshipIdAndStatusIn(relationship.getId(), ACTIVE_CALL_STATUSES)) {
            reservation.cancel();
            log.info("예약 발신 취소(이미 진행 중인 통화 있음). reservationId={}, relationshipId={}",
                    reservationId, relationship.getId());
            return;
        }

        Call call = callRepository.save(
                Call.builder()
                        .relationship(relationship)
                        .sender(CallSender.AI)
                        .build());
        reservation.markFired();

        // 착신 푸시(FCM)는 범위 밖 — 지금은 RINGING Call만 서고, 수락/거절은 accept·reject API로 온다.
        log.info("예약 발신. reservationId={}, callId={}, relationshipId={}",
                reservationId, call.getId(), relationship.getId());
    }

    /**
     * grace window를 넘겨 뒤늦게 발견된 예약을 발신 없이 종결한다. SCHEDULED가 아니면 no-op.
     * <p>무시하고 두면 {@code SCHEDULED}로 영원히 남아 매 tick 인덱스 스캔 대상이 되므로 상태로 닫는다.
     */
    public void expire(Long reservationId) {
        CallReservation reservation = reservationRepository.findByIdForUpdate(reservationId).orElse(null);
        if (reservation == null || reservation.getStatus() != CallReservationStatus.SCHEDULED) {
            return;
        }
        reservation.cancel();
        log.info("예약 만료(발신 시각을 너무 지나 발신하지 않음). reservationId={}, scheduledAt={}",
                reservationId, reservation.getScheduledAt());
    }

    /** 발신 대상 관계인지 — dial과 같은 규칙(살아 있는 메인 캐릭터에게만 통화). */
    private boolean isCallable(Relationship relationship) {
        return relationship.isMain() && relationship.getCharacter().getDeletedAt() == null;
    }
}
