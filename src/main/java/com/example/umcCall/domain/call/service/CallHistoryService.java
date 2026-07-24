package com.example.umcCall.domain.call.service;

import com.example.umcCall.domain.call.dto.response.CallScriptResponse;
import com.example.umcCall.domain.call.entity.Call;
import com.example.umcCall.domain.call.entity.CallHistory;
import com.example.umcCall.domain.call.enums.CallSpeaker;
import com.example.umcCall.domain.call.exception.CallErrorCode;
import com.example.umcCall.domain.call.exception.CallException;
import com.example.umcCall.domain.call.repository.CallHistoryRepository;
import com.example.umcCall.domain.call.repository.CallRepository;
import lombok.RequiredArgsConstructor;
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
}
