package com.example.umcCall.domain.call.service;

import com.example.umcCall.domain.call.dto.response.CallEndResponse;
import com.example.umcCall.domain.call.dto.response.CallIncomingResponse;
import com.example.umcCall.domain.call.dto.response.CallTicketResponse;
import com.example.umcCall.domain.call.entity.Call;
import com.example.umcCall.domain.call.enums.CallSender;
import com.example.umcCall.domain.call.enums.CallStatus;
import com.example.umcCall.domain.call.exception.CallErrorCode;
import com.example.umcCall.domain.call.event.CallEndedEvent;
import com.example.umcCall.domain.call.exception.CallException;
import com.example.umcCall.domain.call.repository.CallRepository;
import com.example.umcCall.domain.call.ticket.WsTicket;
import com.example.umcCall.domain.call.ticket.WsTicketStore;
import com.example.umcCall.domain.relationship.entity.Relationship;
import com.example.umcCall.domain.relationship.repository.RelationshipRepository;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher eventPublisher;

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
     * 착신 대기 중인 내 통화를 조회한다. 착신은 하나뿐이라 단건이고, 없으면 null이다.
     * <p>RINGING이 2건 생기는 엣지(메인 캐릭터 교체)에서는 <b>가장 최근 것</b>을 준다 — 어느 걸 보여줄지
     * 프론트가 고르게 하지 않는다. {@code PENDING}은 티켓이 소비돼 다시 받을 수 없으므로 제외한다.
     */
    @Transactional(readOnly = true)
    public CallIncomingResponse getIncomingCall(Long memberId) {
        return callRepository.findIncomingCalls(memberId, CallStatus.RINGING, LATEST_INCOMING)
                .stream()
                .findFirst()
                .orElse(null);
    }

    /**
     * AI 발신(착신) 수락. 검증은 존재 → 소유 → RINGING 순서이고, wsTicket을 발급한다(dial과 대칭).
     * <p>전이는 PENDING까지다 — IN_PROGRESS는 WS가 열린 뒤 {@code connect()}가 만든다.
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
     * AI 발신(착신) 거절. RINGING → REJECTED. 검증 계단은 accept와 같다.
     * <p>PENDING은 거절 대상이 아니다 — 받은 뒤 끊는 것은 소켓 경로가 CANCELED로 마감한다.
     */
    public void reject(Long memberId, Long callId) {
        loadOwnedRingingCall(memberId, callId).reject();
    }

    /**
     * 사용자가 통화 중에 전화를 끊음. 검증은 존재 → 소유 → 진행 중 순서이고, IN_PROGRESS → COMPLETED다.
     * 연결 전(DIALING/PENDING) 취소는 이 API가 다루지 않는다.
     * <p>⚠ 상태만 바꾸면 소켓이 살아남아 오디오가 계속 CLOVA로 흘러 <b>STT 비용이 발생</b>한다. 세션 정리는
     * {@link CallEndedEvent}를 받는 WS 핸들러가 한다 — 핸들러를 직접 주입하면 순환 참조로 앱이 기동하지 않는다.
     */
    public CallEndResponse end(Long memberId, Long callId) {
        Call call = callRepository.findByIdForUpdate(callId)
                .orElseThrow(() -> new CallException(CallErrorCode.CALL_NOT_FOUND));

        if (!call.getRelationship().getMemberId().equals(memberId)) {
            throw new CallException(CallErrorCode.CALL_ACCESS_DENIED);
        }
        if (call.getStatus() != CallStatus.IN_PROGRESS) {
            throw new CallException(CallErrorCode.CALL_NOT_IN_PROGRESS);
        }
        call.complete();
        eventPublisher.publishEvent(new CallEndedEvent(callId));

        return CallEndResponse.of(call);
    }

    /**
     * 착신 응답(수락/거절)의 공통 검증. 존재 → 소유 → 착신 대기(RINGING) 순서.
     * <p>스위퍼의 부재중 마감과 경쟁하므로 락으로 집는다 — 먼저 마감됐으면 상태 검증에서 409로 떨어진다.
     */
    private Call loadOwnedRingingCall(Long memberId, Long callId) {
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
     * <p>스위퍼가 먼저 마감했으면 상태가 MISSED/CANCELED라 전이가 예외를 던지고, 호출부(WS 핸들러)가
     * 그 소켓을 닫는다.
     */
    public void connect(Long callId) {
        Call call = callRepository.findByIdForUpdate(callId)
                .orElseThrow(() -> new CallException(CallErrorCode.CALL_NOT_FOUND));
        call.connect();
    }

    /**
     * 벨을 너무 오래 울린 통화를 부재중으로 마감한다(스위퍼). RINGING → MISSED.
     * <p>락 뒤 상태 재확인이 "받는 순간 부재중 처리되는" 레이스를 막는다.
     */
    public void markMissed(Long callId) {
        transitionIfStatusIs(callId, CallStatus.RINGING, Call::markMissed);
    }

    /**
     * 받았지만 끝내 접속하지 않은 통화를 마감한다(스위퍼). PENDING → CANCELED.
     * <p>MISSED가 아닌 이유: 사용자는 받았고 실패는 서버/네트워크 사유다. 소켓이 없어 {@code finish()}
     * 트리거가 오지 않으므로 여기서 걷는다.
     */
    public void cancelStalePending(Long callId) {
        transitionIfStatusIs(callId, CallStatus.PENDING, Call::cancel);
    }

    /** 스위퍼 전이의 공통 골격 — 락 → 상태 재확인 → 전이. 상태가 바뀌었으면 no-op. */
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
     * <p>스위퍼의 PENDING 마감과 겹칠 수 있어 다른 전이 경로와 같이 락으로 집는다.
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
