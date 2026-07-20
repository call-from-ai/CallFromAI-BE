package com.example.umcCall.domain.call.service;

import com.example.umcCall.domain.call.dto.response.CallDialResponse;
import com.example.umcCall.domain.call.entity.Call;
import com.example.umcCall.domain.call.enums.CallSender;
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
    public CallDialResponse dial(Long memberId, Long characterId) {
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

        return new CallDialResponse(call.getId(), call.getStatus(), wsTicket);
    }
}
