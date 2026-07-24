package com.example.umcCall.domain.call.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.umcCall.domain.call.dto.response.CallScriptResponse;
import com.example.umcCall.domain.call.entity.Call;
import com.example.umcCall.domain.call.entity.CallHistory;
import com.example.umcCall.domain.call.enums.CallSpeaker;
import com.example.umcCall.domain.call.enums.CallStatus;
import com.example.umcCall.domain.call.exception.CallErrorCode;
import com.example.umcCall.domain.call.exception.CallException;
import com.example.umcCall.domain.call.repository.CallHistoryRepository;
import com.example.umcCall.domain.call.repository.CallRepository;
import com.example.umcCall.domain.relationship.entity.Relationship;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/** 전사 저장(appendHistory)·조회(getScript) 검증. */
@ExtendWith(MockitoExtension.class)
class CallHistoryServiceTest {

    private static final Long CALL_ID = 1L;
    private static final Long MEMBER_ID = 10L;

    @Mock private CallRepository callRepository;
    @Mock private CallHistoryRepository callHistoryRepository;

    @InjectMocks private CallHistoryService callHistoryService;

    @Test
    void appendHistory는_getReferenceById로_FK만_연결해_전사를_저장한다() {
        Call callProxy = mock(Call.class);
        // 프록시만 얻어 FK를 채운다 — 턴마다 Call을 재조회(findById)하지 않는다.
        when(callRepository.getReferenceById(CALL_ID)).thenReturn(callProxy);

        callHistoryService.appendHistory(CALL_ID, CallSpeaker.USER, "안녕");

        ArgumentCaptor<CallHistory> captor = ArgumentCaptor.forClass(CallHistory.class);
        Mockito.verify(callHistoryRepository).save(captor.capture());
        CallHistory saved = captor.getValue();
        assertThat(saved.getSpeaker()).isEqualTo(CallSpeaker.USER);
        assertThat(saved.getContent()).isEqualTo("안녕");
        assertThat(saved.getCall()).isSameAs(callProxy);
        // findById가 아니라 getReferenceById로만 로드했는지(불필요한 SELECT 회피) 확인.
        Mockito.verify(callRepository, Mockito.never()).findById(CALL_ID);
    }

    @Test
    void getScript는_본인_통화의_전사를_발화순서대로_반환한다() {
        LocalDateTime t1 = LocalDateTime.now();
        LocalDateTime t2 = t1.plusSeconds(3);
        // ⚠ when(...) 인자 안에서 헬퍼가 다시 when()을 부르면 중첩 스터빙 오류 → 목을 먼저 만들어 변수로 둔다.
        Call call = ownedCall(CallStatus.COMPLETED);
        CallHistory userLine = line(CallSpeaker.USER, "안녕", t1);
        CallHistory aiLine = line(CallSpeaker.AI, "반가워", t2);
        when(callRepository.findById(CALL_ID)).thenReturn(Optional.of(call));
        when(callHistoryRepository.findByCallIdOrderByIdAsc(CALL_ID))
                .thenReturn(List.of(userLine, aiLine));

        CallScriptResponse response = callHistoryService.getScript(MEMBER_ID, CALL_ID);

        assertThat(response.callId()).isEqualTo(CALL_ID);
        assertThat(response.lines()).hasSize(2);
        assertThat(response.lines().get(0).speaker()).isEqualTo(CallSpeaker.USER);
        assertThat(response.lines().get(0).content()).isEqualTo("안녕");
        assertThat(response.lines().get(0).createdAt()).isEqualTo(t1);
        assertThat(response.lines().get(1).speaker()).isEqualTo(CallSpeaker.AI);
        assertThat(response.lines().get(1).content()).isEqualTo("반가워");
    }

    @Test
    void getScript는_통화가_없으면_CALL_NOT_FOUND를_던진다() {
        when(callRepository.findById(CALL_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> callHistoryService.getScript(MEMBER_ID, CALL_ID))
                .isInstanceOf(CallException.class)
                .extracting(e -> ((CallException) e).getErrorCode())
                .isEqualTo(CallErrorCode.CALL_NOT_FOUND);
    }

    @Test
    void getScript는_본인_통화가_아니면_CALL_ACCESS_DENIED를_던진다() {
        Call othersCall = mock(Call.class);
        Relationship relationship = mock(Relationship.class);
        when(callRepository.findById(CALL_ID)).thenReturn(Optional.of(othersCall));
        when(othersCall.getRelationship()).thenReturn(relationship);
        when(relationship.getMemberId()).thenReturn(MEMBER_ID + 1); // 남의 통화

        assertThatThrownBy(() -> callHistoryService.getScript(MEMBER_ID, CALL_ID))
                .isInstanceOf(CallException.class)
                .extracting(e -> ((CallException) e).getErrorCode())
                .isEqualTo(CallErrorCode.CALL_ACCESS_DENIED);
    }

    @Test
    void getScript는_완료되지_않은_통화면_CALL_NOT_COMPLETED를_던진다() {
        Call inProgressCall = ownedCall(CallStatus.IN_PROGRESS); // 본인 소유지만 아직 진행 중
        when(callRepository.findById(CALL_ID)).thenReturn(Optional.of(inProgressCall));

        assertThatThrownBy(() -> callHistoryService.getScript(MEMBER_ID, CALL_ID))
                .isInstanceOf(CallException.class)
                .extracting(e -> ((CallException) e).getErrorCode())
                .isEqualTo(CallErrorCode.CALL_NOT_COMPLETED);
    }

    /** memberId 소유 + 주어진 상태의 통화 목(mock). */
    private Call ownedCall(CallStatus status) {
        Call call = mock(Call.class);
        Relationship relationship = mock(Relationship.class);
        when(call.getRelationship()).thenReturn(relationship);
        when(relationship.getMemberId()).thenReturn(MEMBER_ID);
        when(call.getStatus()).thenReturn(status);
        return call;
    }

    private CallHistory line(CallSpeaker speaker, String content, LocalDateTime createdAt) {
        CallHistory history = mock(CallHistory.class);
        when(history.getSpeaker()).thenReturn(speaker);
        when(history.getContent()).thenReturn(content);
        when(history.getCreatedAt()).thenReturn(createdAt);
        return history;
    }
}
