package com.example.umcCall.domain.call.service;

import com.example.umcCall.domain.call.dto.response.CallIncomingResponse;
import com.example.umcCall.domain.call.dto.response.CallTicketResponse;
import com.example.umcCall.domain.call.entity.Call;
import com.example.umcCall.domain.call.enums.CallSender;
import com.example.umcCall.domain.call.enums.CallStatus;
import com.example.umcCall.domain.call.exception.CallErrorCode;
import com.example.umcCall.domain.call.exception.CallException;
import com.example.umcCall.domain.call.repository.CallRepository;
import com.example.umcCall.domain.call.ticket.WsTicket;
import com.example.umcCall.domain.call.ticket.WsTicketStore;
import com.example.umcCall.domain.relationship.entity.Relationship;
import com.example.umcCall.domain.relationship.repository.RelationshipRepository;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class CallService {

    /** 착신은 하나뿐이므로 최신 1건만 본다. */
    private static final PageRequest LATEST_INCOMING = PageRequest.of(0, 1);

    private final RelationshipRepository relationshipRepository;
    private final CallRepository callRepository;
    private final WsTicketStore wsTicketStore;

    /**
     * 사용자 발신. characterId의 관계를 확인해 Call을 만들고 단명 wsTicket을 발급한다.
     * <p>검증은 존재 → 소유 → main 순서다 — 남의 캐릭터엔 main 여부를 노출하지 않으려 소유를 먼저 본다.
     * 메인(활성) 캐릭터에게만 통화할 수 있다.
     */
    public CallTicketResponse dial(Long memberId, Long characterId) {
        Relationship relationship = relationshipRepository
                .findByCharacterIdAndCharacterDeletedAtIsNull(characterId)
                .orElseThrow(() -> new CallException(CallErrorCode.CALL_TARGET_NOT_FOUND));

        if (!relationship.getMemberId().equals(memberId)) {
            throw new CallException(CallErrorCode.CALL_TARGET_ACCESS_DENIED);
        }
        if (!relationship.isMain()) {
            throw new CallException(CallErrorCode.CALL_TARGET_NOT_MAIN);
        }

        Call call = callRepository.save(
                Call.builder()
                        .relationship(relationship)
                        .sender(CallSender.USER)
                        .build());

        String wsTicket = wsTicketStore.issue(
                new WsTicket(call.getId(), relationship.getId(), characterId));

        return new CallTicketResponse(call.getId(), call.getStatus(), wsTicket);
    }

    /**
     * 착신 대기 중인 내 통화를 조회한다. 앱 진입·폴링으로 "지금 걸려온 전화"를 발견하는 경로.
     * <p><b>착신은 하나뿐이라 단건을 반환한다</b> — 없으면 null(응답에서 result 키 생략).
     * RINGING이 2건 생기는 엣지(메인 캐릭터 교체)에서는 <b>가장 최근 것</b>을 주고, 어느 걸 보여줄지
     * 프론트가 고르게 하지 않는다. 남은 것은 부재중 스위퍼가 닫는다.
     * <p>{@code PENDING}(이미 받은 통화)은 반환하지 않는다 — 티켓이 소비돼 다시 받을 수 없다.
     * <p>FCM 푸시가 붙어도 이 API는 남는다 — 푸시를 놓친 경우의 복구 경로다.
     */
    @Transactional(readOnly = true)
    public CallIncomingResponse getIncomingCall(Long memberId) {
        return callRepository.findIncomingCalls(memberId, CallStatus.RINGING, LATEST_INCOMING)
                .stream()
                .findFirst()
                .orElse(null);
    }

    /**
     * AI 발신(착신) 수락. 착신 대기 중인 통화에 WebSocket 접속용 wsTicket을 발급한다(dial과 대칭).
     * <p>검증은 존재 → 소유 → RINGING 순서다. 상태는 RINGING → PENDING으로만 전이한다 — IN_PROGRESS는
     * WS가 열려 STT 스트림이 선 뒤 {@code connect()}가 만든다. 티켓만 받고 접속하지 않으면 PENDING으로
     * 남고, 부재중(MISSED) 판정 대상은 RINGING뿐이라 받은 사용자가 부재중으로 오판되지 않는다.
     */
    public CallTicketResponse accept(Long memberId, Long callId) {
        Call call = loadOwnedRingingCall(memberId, callId);
        call.accept();

        Relationship relationship = call.getRelationship();
        String wsTicket = wsTicketStore.issue(
                new WsTicket(call.getId(), relationship.getId(), relationship.getCharacter().getId()));

        return new CallTicketResponse(call.getId(), call.getStatus(), wsTicket);
    }

    /**
     * AI 발신(착신) 거절. RINGING → REJECTED. 티켓을 발급하지 않으므로 반환값이 없다.
     * <p>검증은 accept와 같은 계단(존재 → 소유 → RINGING)이다. PENDING(이미 받은 통화)은 거절 대상이
     * 아니다 — 받은 뒤 끊는 것은 소켓 경로가 CANCELED로 마감한다.
     */
    public void reject(Long memberId, Long callId) {
        loadOwnedRingingCall(memberId, callId).reject();
    }

    /**
     * 착신 응답(수락/거절)의 공통 검증. 존재 → 소유 → 착신 대기(RINGING) 순서로 본다.
     * <p>부재중·거절로 이미 마감된 통화에 늦게 도착한 요청은 여기서 {@code CALL_NOT_RINGING}으로 막힌다.
     */
    private Call loadOwnedRingingCall(Long memberId, Long callId) {
        // 스위퍼의 부재중 마감과 경쟁한다 — 락으로 집어 한쪽만 이기게 한다(먼저 마감됐으면 아래 상태 검증에서 409).
        Call call = callRepository.findByIdForUpdate(callId)
                .orElseThrow(() -> new CallException(CallErrorCode.CALL_NOT_FOUND));

        if (!call.getRelationship().getMemberId().equals(memberId)) {
            throw new CallException(CallErrorCode.CALL_ACCESS_DENIED);
        }
        if (call.getStatus() != CallStatus.RINGING) {
            throw new CallException(CallErrorCode.CALL_NOT_RINGING);
        }
        return call;
    }

    /**
     * 통화 연결됨(WebSocket + STT 스트림 개설 성공). DIALING/PENDING → IN_PROGRESS.
     * <p>엔티티 전이 메서드를 트랜잭션 안에서 호출해 dirty checking으로 반영한다.
     * <p>⚠ <b>비관적 락으로 집는다</b> — 스위퍼의 마감(부재중/미접속 처리)과 겹칠 수 있고, 그때
     * 한쪽만 이겨야 한다. 스위퍼가 먼저 이겼으면 상태가 MISSED/CANCELED라 {@code connect()}가
     * 예외를 던지고, 호출부(WS 핸들러)가 그 소켓을 닫는다.
     */
    public void connect(Long callId) {
        Call call = callRepository.findByIdForUpdate(callId)
                .orElseThrow(() -> new CallException(CallErrorCode.CALL_NOT_FOUND));
        call.connect();
    }

    /**
     * 벨을 너무 오래 울린 통화를 부재중으로 마감한다(스위퍼). RINGING → MISSED.
     * <p>잠근 뒤 <b>상태를 다시 확인</b>한다 — 그 사이 사용자가 받았거나(PENDING) 거절했으면(REJECTED)
     * 마감하지 않는다. 이 재확인이 "받는 순간 부재중 처리되는" 레이스를 막는다.
     */
    public void markMissed(Long callId) {
        transitionIfStatusIs(callId, CallStatus.RINGING, Call::markMissed);
    }

    /**
     * 받았지만 끝내 접속하지 않은 통화를 마감한다(스위퍼). PENDING → CANCELED.
     * <p>사용자는 받았고 실패는 서버/네트워크 사유라 부재중이 아니다. 소켓이 열리지 않아
     * {@code finish()} 트리거가 없는 통화를 여기서 걷는다.
     */
    public void cancelStalePending(Long callId) {
        transitionIfStatusIs(callId, CallStatus.PENDING, Call::cancel);
    }

    /** 스위퍼 전이의 공통 골격 — claim(락) → 상태 재확인 → 전이. 상태가 바뀌었으면 조용히 no-op. */
    private void transitionIfStatusIs(Long callId, CallStatus expected, Consumer<Call> transition) {
        Call call = callRepository.findByIdForUpdate(callId).orElse(null);
        if (call == null || call.getStatus() != expected) {
            return;
        }
        transition.accept(call);
    }

    /**
     * 통화 종료(소켓 끊김). 연결됐었으면 정상 완료, 연결 전이었으면 취소로 마감한다.
     * <p>PENDING(착신을 받았지만 스트림 개설이 실패한 경우)도 서버 측 사유라 CANCELED로 닫는다.
     * 이미 종료 상태면 no-op(정리 경로가 겹쳐도 안전). RINGING은 소켓을 열 수 없어 여기 도달하지 않는다.
     * <p>스위퍼(PENDING 마감)와 겹칠 수 있어 다른 전이 경로와 같이 락으로 집는다 — 겹쳐도 결과는
     * 같은 CANCELED지만, 상태를 읽고 전이하는 구간이 갈라지지 않게 한다.
     */
    public void finish(Long callId) {
        Call call = callRepository.findByIdForUpdate(callId)
                .orElseThrow(() -> new CallException(CallErrorCode.CALL_NOT_FOUND));
        switch (call.getStatus()) {
            case IN_PROGRESS -> call.complete();
            case DIALING, PENDING -> call.cancel();
            default -> { /* 이미 종료 상태 등 — 전이 없음 */ }
        }
    }
}
