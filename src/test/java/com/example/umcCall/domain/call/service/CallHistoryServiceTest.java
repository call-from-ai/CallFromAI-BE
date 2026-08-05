package com.example.umcCall.domain.call.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.umcCall.domain.call.dto.response.CallListItem;
import com.example.umcCall.domain.call.dto.response.CallListResponse;
import com.example.umcCall.domain.call.dto.response.CallScriptResponse;
import com.example.umcCall.domain.call.entity.Call;
import com.example.umcCall.domain.call.entity.CallHistory;
import com.example.umcCall.domain.call.enums.CallSender;
import com.example.umcCall.domain.call.enums.CallSpeaker;
import com.example.umcCall.domain.call.enums.CallStatus;
import com.example.umcCall.domain.call.enums.CallSummaryStatus;
import com.example.umcCall.domain.call.exception.CallErrorCode;
import com.example.umcCall.domain.call.exception.CallException;
import com.example.umcCall.domain.call.repository.CallHistoryRepository;
import com.example.umcCall.domain.call.repository.CallRepository;
import com.example.umcCall.domain.character.entity.Character;
import com.example.umcCall.domain.relationship.entity.Relationship;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.data.domain.Pageable;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/** 전사 저장(appendHistory)·조회(getScript) 검증. */
@ExtendWith(MockitoExtension.class)
class CallHistoryServiceTest {

    private static final Long CALL_ID = 1L;
    private static final Long MEMBER_ID = 10L;

    @Mock private CallRepository callRepository;
    @Mock private CallHistoryRepository callHistoryRepository;
    @Mock private CallArtifactRegistry callArtifactRegistry;
    @Mock private TransactionTemplate transactionTemplate;

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

    @Test
    void getCallList는_종료된_통화만_최신순_최대20건을_요청해_반환한다() {
        CallListItem item1 = new CallListItem(
                12L, "민준", CallSender.USER, "오늘하루와 퇴근 후 일상 이야기", CallSummaryStatus.READY,
                LocalDateTime.now(), CallStatus.COMPLETED);
        CallListItem item2 = new CallListItem(
                9L, "동휘", CallSender.USER, null, CallSummaryStatus.NONE,
                LocalDateTime.now().minusHours(1), CallStatus.CANCELED);
        when(callRepository.findRecentCallList(eq(MEMBER_ID), anySet(), any(Pageable.class)))
                .thenReturn(List.of(item1, item2));

        CallListResponse response = callHistoryService.getCallList(MEMBER_ID);

        assertThat(response.content()).containsExactly(item1, item2);
        // 터미널 상태 4개만 + 최대 20건(page 0)으로 요청하는지 검증.
        ArgumentCaptor<Collection<CallStatus>> statusCaptor = ArgumentCaptor.forClass(Collection.class);
        ArgumentCaptor<Pageable> pageCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(callRepository).findRecentCallList(eq(MEMBER_ID), statusCaptor.capture(), pageCaptor.capture());
        assertThat(statusCaptor.getValue()).containsExactlyInAnyOrder(
                CallStatus.COMPLETED, CallStatus.CANCELED, CallStatus.MISSED, CallStatus.REJECTED);
        assertThat(pageCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageCaptor.getValue().getPageSize()).isEqualTo(20);
    }

    // --- 상세 조회의 산출물 대기 (?wait=true) -----------------------------------------------------

    @Test
    void getCallDetail은_wait이면_산출물을_기다린_뒤_다시_읽는다() {
        // 이게 이 기능의 전부다 — 기다리기만 하고 다시 안 읽으면 준비 전 스냅샷(PROCESSING)이 그대로 나간다.
        Call call = detailCall();
        when(callRepository.findById(CALL_ID)).thenReturn(Optional.of(call));
        when(callArtifactRegistry.await(CALL_ID)).thenReturn(true); // 실제로 기다렸다
        givenTransactionRunsInline();

        callHistoryService.getCallDetail(MEMBER_ID, CALL_ID, true);

        verify(callRepository, Mockito.times(2)).findById(CALL_ID);
    }

    @Test
    void getCallDetail은_기다릴_대상이_없으면_재조회하지_않는다() {
        // 이미 끝났거나 산출물이 없는 통화. 다시 읽어봐야 같은 값이라 쿼리만 늘어난다.
        Call call = detailCall();
        when(callRepository.findById(CALL_ID)).thenReturn(Optional.of(call));
        when(callArtifactRegistry.await(CALL_ID)).thenReturn(false);
        givenTransactionRunsInline();

        callHistoryService.getCallDetail(MEMBER_ID, CALL_ID, true);

        verify(callRepository, Mockito.times(1)).findById(CALL_ID);
    }

    @Test
    void getCallDetail은_wait이_아니면_기다리지_않는다() {
        // 목록에서 들어온 평범한 조회까지 붙잡으면 안 된다 — 대기는 종료 화면이 명시적으로 요청할 때만이다.
        Call call = detailCall();
        when(callRepository.findById(CALL_ID)).thenReturn(Optional.of(call));
        givenTransactionRunsInline();

        callHistoryService.getCallDetail(MEMBER_ID, CALL_ID, false);

        Mockito.verify(callArtifactRegistry, Mockito.never()).await(any());
        verify(callRepository, Mockito.times(1)).findById(CALL_ID);
    }

    @Test
    void getCallDetail은_기다리기_전에_소유를_검증한다() {
        // ⚠ 남의 통화 ID로 요청 스레드를 붙잡을 수 없어야 한다(존재 → 소유 → 상태 계단 그대로).
        Call othersCall = mock(Call.class);
        Relationship relationship = mock(Relationship.class);
        when(callRepository.findById(CALL_ID)).thenReturn(Optional.of(othersCall));
        when(othersCall.getRelationship()).thenReturn(relationship);
        when(relationship.getMemberId()).thenReturn(MEMBER_ID + 1);
        givenTransactionRunsInline();

        assertThatThrownBy(() -> callHistoryService.getCallDetail(MEMBER_ID, CALL_ID, true))
                .isInstanceOf(CallException.class)
                .extracting(e -> ((CallException) e).getErrorCode())
                .isEqualTo(CallErrorCode.CALL_ACCESS_DENIED);
        Mockito.verify(callArtifactRegistry, Mockito.never()).await(any());
    }

    /** 대기가 트랜잭션 밖이라 조회 본체만 템플릿으로 감싼다 — 테스트에선 그 자리에서 실행시킨다. */
    private void givenTransactionRunsInline() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation ->
                invocation.<TransactionCallback<Object>>getArgument(0).doInTransaction(null));
    }

    /** 상세 응답까지 만들 수 있는(캐릭터 이름 포함) 본인 소유 완료 통화. */
    private Call detailCall() {
        Call call = mock(Call.class);
        Relationship relationship = mock(Relationship.class);
        Character character = mock(Character.class);
        when(call.getRelationship()).thenReturn(relationship);
        when(relationship.getMemberId()).thenReturn(MEMBER_ID);
        when(relationship.getCharacter()).thenReturn(character);
        when(character.getFirstName()).thenReturn("민준");
        when(call.getStatus()).thenReturn(CallStatus.COMPLETED);
        return call;
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
