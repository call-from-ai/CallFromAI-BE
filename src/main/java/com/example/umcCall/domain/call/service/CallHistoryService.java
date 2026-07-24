package com.example.umcCall.domain.call.service;

import com.example.umcCall.domain.call.dto.response.CallListResponse;
import com.example.umcCall.domain.call.dto.response.CallScriptResponse;
import com.example.umcCall.domain.call.entity.Call;
import com.example.umcCall.domain.call.entity.CallHistory;
import com.example.umcCall.domain.call.enums.CallSpeaker;
import com.example.umcCall.domain.call.enums.CallStatus;
import com.example.umcCall.domain.call.exception.CallErrorCode;
import com.example.umcCall.domain.call.exception.CallException;
import com.example.umcCall.domain.call.repository.CallHistoryRepository;
import com.example.umcCall.domain.call.repository.CallRepository;
import java.util.EnumSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 통화 전사(transcript) 저장·조회 담당. 저장은 통화 중 워커가, 조회는 통화 종료 후 클라이언트가 부른다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CallHistoryService {

    private final CallRepository callRepository;
    private final CallHistoryRepository callHistoryRepository;

    /** 목록에 노출할 통화 상태 = 종료된 것만. 진행 중(DIALING/RINGING/IN_PROGRESS)은 아직 기록이 아니라 제외한다. */
    private static final Set<CallStatus> TERMINAL_STATUSES = EnumSet.of(
            CallStatus.COMPLETED, CallStatus.CANCELED, CallStatus.MISSED, CallStatus.REJECTED);

    /** 최근 통화 노출 개수. 프론트와 20건 고정 합의(페이지네이션 없음). */
    private static final int RECENT_CALL_LIMIT = 20;

    /**
     * 통화 전사 한 줄을 저장한다. 발화가 일어난 순간마다(USER final · AI TTS 송신 성공) 호출되는 append-only 저장.
     * <p>callId로 Call 프록시({@code getReferenceById})만 얻어 FK를 채우므로 턴마다 Call을 재조회하지 않는다.
     * <p>이 메서드의 트랜잭션은 짧게 끝난다 — {@code chat()}의 느린 REST는 이 트랜잭션 밖(호출부 워커)에서 돈다.
     */
    public void appendHistory(Long callId, CallSpeaker speaker, String content) {
        callHistoryRepository.save(
                CallHistory.builder()
                        .call(callRepository.getReferenceById(callId))
                        .speaker(speaker)
                        .content(content)
                        .build());
    }

    /**
     * 통화 전사(script) 전문을 조회한다. 발화 순서(id ASC)대로 전체를 반환한다(페이지네이션 없음).
     * <p>검증: 통화 존재({@code CALL_NOT_FOUND}) → 본인 소유({@code CALL_ACCESS_DENIED}) →
     * 완료 상태({@code CALL_NOT_COMPLETED}). <b>완료된 통화만 전문을 준다</b> — 진행 중/취소/거절/부재중은 막는다
     * (전문은 종료된 통화의 완결된 기록이라는 계약). 존재·소유·완료를 백엔드가 구분해 내려주고, 화면 분기는 프론트가 정한다.
     */
    @Transactional(readOnly = true)
    public CallScriptResponse getScript(Long memberId, Long callId) {
        Call call = callRepository.findById(callId)
                .orElseThrow(() -> new CallException(CallErrorCode.CALL_NOT_FOUND));
        if (!call.getRelationship().getMemberId().equals(memberId)) {
            throw new CallException(CallErrorCode.CALL_ACCESS_DENIED);
        }
        if (call.getStatus() != CallStatus.COMPLETED) {
            throw new CallException(CallErrorCode.CALL_NOT_COMPLETED);
        }
        return CallScriptResponse.of(callId, callHistoryRepository.findByCallIdOrderByIdAsc(callId));
    }

    /**
     * 내 통화 목록을 조회한다. <b>종료된 통화(COMPLETED/CANCELED/MISSED/REJECTED)만 최신순 최대 20건</b>.
     * <p>진행 중인 통화는 아직 기록이 아니라 제외한다. 페이지네이션 없음(20건 고정 합의).
     * 캐릭터 이름까지 리포지토리가 조인·프로젝션해 N+1 없이 한 방에 가져온다.
     */
    @Transactional(readOnly = true)
    public CallListResponse getCallList(Long memberId) {
        return CallListResponse.of(callRepository.findRecentCallList(
                memberId, TERMINAL_STATUSES, PageRequest.of(0, RECENT_CALL_LIMIT)));
    }
}
