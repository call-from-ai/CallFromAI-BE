package com.example.umcCall.domain.call.service;

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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class CallService {

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
     * AI 발신(착신) 수락. 착신 대기 중인 통화에 WebSocket 접속용 wsTicket을 발급한다(dial과 대칭).
     * <p>검증은 존재 → 소유 → RINGING 순서다. 상태는 RINGING → PENDING으로만 전이한다 — IN_PROGRESS는
     * WS가 열려 STT 스트림이 선 뒤 {@code connect()}가 만든다. 티켓만 받고 접속하지 않으면 PENDING으로
     * 남고, 부재중(MISSED) 판정 대상은 RINGING뿐이라 받은 사용자가 부재중으로 오판되지 않는다.
     */
    public CallTicketResponse accept(Long memberId, Long callId) {
        Call call = callRepository.findById(callId)
                .orElseThrow(() -> new CallException(CallErrorCode.CALL_NOT_FOUND));

        Relationship relationship = call.getRelationship();
        if (!relationship.getMemberId().equals(memberId)) {
            throw new CallException(CallErrorCode.CALL_ACCESS_DENIED);
        }
        if (call.getStatus() != CallStatus.RINGING) {
            throw new CallException(CallErrorCode.CALL_NOT_RINGING);
        }
        call.accept();

        String wsTicket = wsTicketStore.issue(
                new WsTicket(call.getId(), relationship.getId(), relationship.getCharacter().getId()));

        return new CallTicketResponse(call.getId(), call.getStatus(), wsTicket);
    }

    /**
     * 통화 연결됨(WebSocket + STT 스트림 개설 성공). DIALING → IN_PROGRESS.
     * <p>엔티티 전이 메서드를 트랜잭션 안에서 호출해 dirty checking으로 반영한다.
     */
    public void connect(Long callId) {
        Call call = callRepository.findById(callId)
                .orElseThrow(() -> new CallException(CallErrorCode.CALL_NOT_FOUND));
        call.connect();
    }

    /**
     * 통화 종료(소켓 끊김). 연결됐었으면 정상 완료, 연결 전이었으면 취소로 마감한다.
     * <p>PENDING(착신을 받았지만 스트림 개설이 실패한 경우)도 서버 측 사유라 CANCELED로 닫는다.
     * 이미 종료 상태면 no-op(정리 경로가 겹쳐도 안전). RINGING은 소켓을 열 수 없어 여기 도달하지 않는다.
     */
    public void finish(Long callId) {
        Call call = callRepository.findById(callId)
                .orElseThrow(() -> new CallException(CallErrorCode.CALL_NOT_FOUND));
        switch (call.getStatus()) {
            case IN_PROGRESS -> call.complete();
            case DIALING, PENDING -> call.cancel();
            default -> { /* 이미 종료 상태 등 — 전이 없음 */ }
        }
    }
}
