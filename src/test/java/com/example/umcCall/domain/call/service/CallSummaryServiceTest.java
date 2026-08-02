package com.example.umcCall.domain.call.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.umcCall.domain.ai.client.AiServerClient;
import com.example.umcCall.domain.ai.dto.AiCallTopicRequest;
import com.example.umcCall.domain.ai.dto.AiCallTopicResponse;
import com.example.umcCall.domain.call.entity.Call;
import com.example.umcCall.domain.call.entity.CallHistory;
import com.example.umcCall.domain.call.enums.CallSender;
import com.example.umcCall.domain.call.enums.CallSpeaker;
import com.example.umcCall.domain.call.enums.CallSummaryStatus;
import com.example.umcCall.domain.call.repository.CallHistoryRepository;
import com.example.umcCall.domain.call.repository.CallRepository;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 통화 요약이 통화에 어떻게 남는지를 고정하는 테스트.
 * <p>프론트가 {@code summaryStatus}로 화면과 재시도 여부를 고르므로, 여기가 틀리면 "준비 중"에
 * 영영 갇히거나 있는 요약을 없는 것으로 그린다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CallSummaryServiceTest {

    private static final Long CALL_ID = 7L;

    @Mock private AiServerClient aiServerClient;
    @Mock private CallRepository callRepository;
    @Mock private CallHistoryRepository callHistoryRepository;
    @Mock private TransactionTemplate transactionTemplate;

    private CallSummaryService service;
    private Call call;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        call = Call.builder().sender(CallSender.AI).build();
        when(callRepository.findById(CALL_ID)).thenReturn(Optional.of(call));
        // 트랜잭션 경계는 검증 대상이 아니라 그 자리에서 바로 실행한다.
        doAnswer(invocation -> {
            ((Consumer<TransactionStatus>) invocation.getArgument(0)).accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        service = new CallSummaryService(
                aiServerClient, callRepository, callHistoryRepository, transactionTemplate);
    }

    private void givenTranscript(CallHistory... rows) {
        when(callHistoryRepository.findByCallIdOrderByIdAsc(CALL_ID)).thenReturn(List.of(rows));
    }

    private static CallHistory history(CallSpeaker speaker, String content) {
        return CallHistory.builder().speaker(speaker).content(content).build();
    }

    /** 생성은 비동기지만 future로 끝을 알 수 있다 — 테스트는 그걸 기다린다(폴링·sleep 불필요). */
    private void awaitGenerate() {
        service.generate(CALL_ID).join();
    }

    @Test
    void 전사를_주제_라벨로_요약해_남긴다() {
        givenTranscript(
                history(CallSpeaker.USER, "오늘 퇴근하고 뭐 했어?"),
                history(CallSpeaker.AI, "집에서 쉬었어."));
        when(aiServerClient.summarizeCallTopic(any()))
                .thenReturn(new AiCallTopicResponse("오늘하루와 퇴근 후 일상 이야기"));

        awaitGenerate();

        assertThat(call.getAiSummary()).isEqualTo("오늘하루와 퇴근 후 일상 이야기");
        assertThat(call.getSummaryStatus()).isEqualTo(CallSummaryStatus.READY);
    }

    @Test
    void 전사를_시간순_role로_변환해_보낸다() {
        givenTranscript(
                history(CallSpeaker.USER, "안녕"),
                history(CallSpeaker.AI, "안녕!"));
        when(aiServerClient.summarizeCallTopic(any())).thenReturn(new AiCallTopicResponse("인사"));

        awaitGenerate();

        ArgumentCaptor<AiCallTopicRequest> captor = ArgumentCaptor.forClass(AiCallTopicRequest.class);
        verify(aiServerClient).summarizeCallTopic(captor.capture());
        AiCallTopicRequest request = captor.getValue();
        // role은 AI 계약대로 소문자다(채팅과 공통) — 대문자면 서버가 400을 준다.
        assertThat(request.messages()).extracting("role").containsExactly("user", "assistant");
        assertThat(request.messages()).extracting("content").containsExactly("안녕", "안녕!");
        assertThat(request.callId()).isEqualTo(CALL_ID);
    }

    @Test
    void 전사가_없으면_LLM을_부르지_않고_NONE으로_남긴다() {
        // 미연결·즉시종료 통화까지 요약 비용을 낼 이유가 없다.
        givenTranscript();

        awaitGenerate();

        verify(aiServerClient, never()).summarizeCallTopic(any());
        assertThat(call.getSummaryStatus()).isEqualTo(CallSummaryStatus.NONE);
        assertThat(call.getAiSummary()).isNull();
    }

    @Test
    void 빈_내용만_있는_전사도_요약하지_않는다() {
        givenTranscript(history(CallSpeaker.USER, "   "));

        awaitGenerate();

        verify(aiServerClient, never()).summarizeCallTopic(any());
        assertThat(call.getSummaryStatus()).isEqualTo(CallSummaryStatus.NONE);
    }

    @Test
    void 요약이_실패해도_예외를_던지지_않고_FAILED로_남긴다() {
        // fail-open — 통화는 이미 끝났고, 요약이 없다고 통화·전사·녹음이 달라지지 않는다.
        givenTranscript(history(CallSpeaker.USER, "안녕"));
        when(aiServerClient.summarizeCallTopic(any())).thenThrow(new RuntimeException("AI 서버 다운"));

        assertThatCode(this::awaitGenerate).doesNotThrowAnyException();

        assertThat(call.getSummaryStatus()).isEqualTo(CallSummaryStatus.FAILED);
        assertThat(call.getAiSummary()).isNull();
    }

    @Test
    void 전사_조회가_실패해도_통화를_망가뜨리지_않는다() {
        when(callHistoryRepository.findByCallIdOrderByIdAsc(CALL_ID))
                .thenThrow(new RuntimeException("DB 오류"));

        assertThatCode(this::awaitGenerate).doesNotThrowAnyException();

        verify(aiServerClient, never()).summarizeCallTopic(any());
        assertThat(call.getSummaryStatus()).isEqualTo(CallSummaryStatus.NONE);
    }

    @Test
    void 생성_중에는_PROCESSING을_거친다() {
        // ⚠ 이 사이에 사용자가 상세를 열면 "요약 없음"으로 오판한다 — 그래서 미리 찍어야 한다.
        givenTranscript(history(CallSpeaker.USER, "안녕"));
        when(aiServerClient.summarizeCallTopic(any())).thenAnswer(invocation -> {
            assertThat(call.getSummaryStatus()).isEqualTo(CallSummaryStatus.PROCESSING);
            return new AiCallTopicResponse("인사");
        });

        awaitGenerate();

        assertThat(call.getSummaryStatus()).isEqualTo(CallSummaryStatus.READY);
    }

    @Test
    void 라벨_길이_상한을_함께_보낸다() {
        givenTranscript(history(CallSpeaker.USER, "안녕"));
        when(aiServerClient.summarizeCallTopic(any())).thenReturn(new AiCallTopicResponse("인사"));

        awaitGenerate();

        ArgumentCaptor<AiCallTopicRequest> captor = ArgumentCaptor.forClass(AiCallTopicRequest.class);
        verify(aiServerClient).summarizeCallTopic(captor.capture());
        assertThat(captor.getValue().maxCharacters()).isEqualTo(20);
    }
}
