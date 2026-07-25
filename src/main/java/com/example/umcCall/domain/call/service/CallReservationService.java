package com.example.umcCall.domain.call.service;

import com.example.umcCall.domain.call.dto.response.CallReservationListResponse;
import com.example.umcCall.domain.call.entity.Call;
import com.example.umcCall.domain.call.entity.CallReservation;
import com.example.umcCall.domain.call.enums.CallReservationStatus;
import com.example.umcCall.domain.call.enums.CallSender;
import com.example.umcCall.domain.call.enums.CallStatus;
import com.example.umcCall.domain.call.exception.CallErrorCode;
import com.example.umcCall.domain.call.exception.CallException;
import com.example.umcCall.domain.call.repository.CallRepository;
import com.example.umcCall.domain.call.repository.CallReservationRepository;
import com.example.umcCall.domain.relationship.entity.Relationship;
import com.example.umcCall.domain.relationship.repository.RelationshipRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 통화 예약 생성·조회·수정과, 스케줄러({@code CallReservationWorker})가 골라 온 예약의 발신 처리.
 *
 * <p>생성({@link #reserve})만 HTTP 진입점이 없다 — 다른 도메인(채팅·proactive)이 자바 메서드로 부른다.
 *
 * <p>⚠ <b>예약 1건 = 트랜잭션 1개</b>다 — 한 예약이 실패해도 나머지가 처리되고 락을 오래 잡지 않는다.
 * 루프는 워커가 트랜잭션 밖에서 돈다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class CallReservationService {

    /**
     * "이미 통화 중"으로 볼 상태. RINGING·PENDING도 포함한다(벨이 울리는 중에 또 울리면 안 된다).
     * ⚠ 이 집합은 스위퍼가 두 상태를 유계로 유지하는 걸 전제로 한다 — 스위퍼를 빼면 함께 재검토할 것.
     */
    private static final Set<CallStatus> ACTIVE_CALL_STATUSES =
            EnumSet.of(CallStatus.DIALING, CallStatus.RINGING,
                    CallStatus.PENDING, CallStatus.IN_PROGRESS);

    /** 오늘 예약 목록의 끝 경계(다음날 이 시각 직전까지). 새벽 예약도 "오늘"로 보이게 넉넉히 둔다. */
    private static final int DAILY_WINDOW_END_HOUR = 5;

    private final CallReservationRepository reservationRepository;
    private final CallRepository callRepository;
    private final RelationshipRepository relationshipRepository;

    /**
     * 예약을 생성한다. <b>같은 서버 안의 다른 도메인(채팅·proactive)이 호출하는 진입점</b>이고,
     * HTTP API는 없다 — 사용자가 직접 예약을 만드는 화면이 아니라 대화 중 약속이 예약이 되기 때문이다.
     * 호출자는 시각만 정하고, 예약 가능 여부·불변식은 전부 여기서 본다.
     *
     * <p>검증은 관계 유효(존재·미삭제 → 메인) → 시각 → 중복 순서다. 관계 규칙은 dial·fire와 같다
     * (살아 있는 메인 캐릭터에게만 통화). 지난 시각을 막는 이유: 스케줄러가 grace window 밖으로 보고
     * 발신 없이 {@code CANCELED}로 닫아버리므로 애초에 받지 않는다.
     *
     * <p>⚠ <b>관계당 대기 중 예약은 1건</b>이다 — 대화 중 AI가 여러 번 약속하면 예약이 쌓여 같은
     * 상대에게 하루에 여러 번 전화가 간다. 시각을 옮기려면 {@link #reschedule}을 쓴다.
     * (동시 호출로 2건이 들어가는 창은 막지 않는다: 실제 호출은 한 대화 흐름에서 오고, 그래도 새는 경우
     * 두 번째 발신이 첫 통화의 진행 중 상태를 보고 스스로 CANCELED로 닫힌다.)
     *
     * @return 생성된 예약 id
     */
    public Long reserve(Long relationshipId, LocalDateTime scheduledAt) {
        Relationship relationship = relationshipRepository.findById(relationshipId)
                .orElseThrow(() -> new CallException(CallErrorCode.CALL_TARGET_NOT_FOUND));

        if (relationship.getCharacter().getDeletedAt() != null) {
            throw new CallException(CallErrorCode.CALL_TARGET_NOT_FOUND);
        }
        if (!relationship.isMain()) {
            throw new CallException(CallErrorCode.CALL_TARGET_NOT_MAIN);
        }
        if (!scheduledAt.isAfter(LocalDateTime.now())) {
            throw new CallException(CallErrorCode.CALL_RESERVATION_PAST_TIME);
        }
        if (reservationRepository.existsByRelationshipIdAndStatus(
                relationshipId, CallReservationStatus.SCHEDULED)) {
            throw new CallException(CallErrorCode.CALL_RESERVATION_ALREADY_EXISTS);
        }

        CallReservation reservation = reservationRepository.save(
                CallReservation.builder()
                        .relationship(relationship)
                        .scheduledAt(scheduledAt)
                        .build());

        log.info("예약 생성. reservationId={}, relationshipId={}, scheduledAt={}",
                reservation.getId(), relationshipId, scheduledAt);
        return reservation.getId();
    }

    /**
     * 오늘 하루의 대기 중 예약을 가까운 시각부터 조회한다(페이지네이션 없음).
     * <p>창은 <b>당일 00:00 ~ 다음날 05:00</b> — 체감상 "오늘 밤"인 새벽 예약이 자정을 넘겼다고
     * 사라지지 않게 넉넉히 잡았다. 지난 예약의 결과는 통화 기록({@code GET /calls})에서 본다.
     */
    @Transactional(readOnly = true)
    public CallReservationListResponse getMyReservations(Long memberId) {
        LocalDate today = LocalDate.now();
        return CallReservationListResponse.of(reservationRepository.findMyReservations(
                memberId,
                CallReservationStatus.SCHEDULED,
                today.atStartOfDay(),
                today.plusDays(1).atTime(DAILY_WINDOW_END_HOUR, 0)));
    }

    /**
     * 예약 시각을 변경한다. 검증은 존재 → 소유 → 대기 중 순서이고, 미래 시각인지는 요청 검증이 본다.
     * <p>⚠ 스케줄러의 fire/expire와 경쟁하므로 락으로 집는다 — 방금 발신된 예약을 뒤늦게 옮기면
     * 이미 울린 통화와 예약 시각이 어긋난다.
     */
    public void reschedule(Long memberId, Long reservationId, LocalDateTime scheduledAt) {
        CallReservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new CallException(CallErrorCode.CALL_RESERVATION_NOT_FOUND));

        if (!reservation.getRelationship().getMemberId().equals(memberId)) {
            throw new CallException(CallErrorCode.CALL_RESERVATION_ACCESS_DENIED);
        }
        if (reservation.getStatus() != CallReservationStatus.SCHEDULED) {
            throw new CallException(CallErrorCode.CALL_RESERVATION_NOT_SCHEDULED);
        }
        reservation.reschedule(scheduledAt);
    }

    /**
     * 예약 1건을 발신으로 옮긴다. 발신하면 FIRED, 발신 조건이 아니면 CANCELED로 종결한다.
     * <p>락 뒤 상태를 다시 확인한다 — 다른 인스턴스·중복 tick이 먼저 집었으면 no-op(한 예약은 한 번만 울린다).
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
     * <p>무시하면 SCHEDULED로 영원히 남아 매 tick 조회에 걸리므로 상태로 닫는다.
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
