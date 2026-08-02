package com.example.umcCall.domain.call.service;

import com.example.umcCall.domain.ai.client.AiServerClient;
import com.example.umcCall.domain.ai.dto.AiCallTopicRequest;
import com.example.umcCall.domain.ai.dto.AiSummaryMessage;
import com.example.umcCall.domain.call.entity.Call;
import com.example.umcCall.domain.call.enums.CallSpeaker;
import com.example.umcCall.domain.call.repository.CallHistoryRepository;
import com.example.umcCall.domain.call.repository.CallRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 마감된 통화의 전사를 <b>주제 라벨</b> 한 줄로 요약해 {@code Call.aiSummary}·{@code summaryStatus}에 남긴다.
 *
 * <p><b>fail-open이다.</b> 생성이 실패해도 예외를 밖으로 내보내지 않는다 — 통화는 이미 끝났고,
 * 요약이 없다고 통화·전사·녹음이 달라지지 않는다. 실패는 {@code FAILED}로 남겨 프론트가 "준비 중"에
 * 갇히지 않게 한다.
 *
 * <p>⚠ <b>호출부가 결과를 기다릴 수 있도록 {@link CompletableFuture}를 돌려준다.</b> 통화 종료 응답
 * ({@code PATCH /calls/{id}/end}의 AFTER_COMMIT) 안에서 상한까지 기다렸다가 응답하기 위해서다 —
 * 그래야 프론트가 폴링 없이 한 번의 조회로 요약을 받는다. 상한을 넘기면 호출부가 <b>기다리기를 포기할 뿐</b>
 * 작업은 여기서 계속 돈다(취소하지 않는다).
 *
 * @see CallRecordingService 같은 파이프라인을 도는 짝. 둘은 <b>서로 다른 풀에서 병렬로</b> 돌아야
 *      종료 응답 지연이 둘의 합이 아니라 느린 쪽 하나가 된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CallSummaryService {

    /**
     * 주제 라벨 길이 상한(공백 포함). 기대하는 모양은 "오늘하루와 퇴근 후 일상 이야기" 정도다.
     * <p>⚠ {@code Call.aiSummary} 컬럼은 150자라 여유가 크다 — 이 값을 늘릴 땐 컬럼이 아니라
     * <b>라벨의 성격</b>(한 문장)이 상한이라는 걸 기억할 것.
     */
    private static final int MAX_TOPIC_CHARACTERS = 20;

    /** 앱 종료 시 진행 중인 생성을 기다려 주는 시간. 넘기면 포기한다(그 요약만 잃는다). */
    private static final long SHUTDOWN_WAIT_SECONDS = 10;

    /**
     * 요약 생성 풀. ⚠ <b>단일 스레드로 두지 않는다</b> — 종료 응답이 이 결과를 기다리므로,
     * 동시에 끝난 통화들이 줄을 서면 뒤 통화의 <b>끊기 응답이 앞 통화의 LLM 시간만큼 밀린다</b>.
     * LLM 대기는 CPU가 아니라 I/O라 몇 개 더 두는 비용이 거의 없다.
     */
    private final ExecutorService summarizer = Executors.newFixedThreadPool(4);

    private final AiServerClient aiServerClient;
    private final CallRepository callRepository;
    private final CallHistoryRepository callHistoryRepository;
    private final TransactionTemplate transactionTemplate;

    /**
     * 통화 하나의 요약 생성을 시작한다. 호출부(WS 핸들러 정리 경로)를 잡아두지 않는다.
     *
     * @return 생성이 끝나면(성공·실패 무관) 완료되는 future. 호출부는 이걸 상한까지만 기다린다.
     */
    public CompletableFuture<Void> generate(Long callId) {
        try {
            return CompletableFuture.runAsync(() -> summarize(callId), summarizer);
        } catch (RejectedExecutionException e) {
            // 앱 종료로 풀이 내려간 뒤. 상태는 NONE으로 남는다.
            log.warn("[Summary] 요약 풀 종료됨 → 생성을 건너뛴다. callId={}", callId);
            return CompletableFuture.completedFuture(null);
        }
    }

    private void summarize(Long callId) {
        long startedAt = System.nanoTime();
        List<AiSummaryMessage> messages = loadTranscript(callId);
        // ⚠ 전사가 없으면 LLM을 부르지 않는다 — 미연결·즉시종료 통화까지 요약 비용을 낼 이유가 없다.
        // FAILED가 아니라 NONE인 이유는 "실패"가 아니라 "요약할 대화가 없음"이라서다(프론트 문구가 갈린다).
        if (messages.isEmpty()) {
            log.debug("[Summary] 전사가 없어 요약을 건너뜀. callId={}", callId);
            updateCall(callId, Call::markSummaryUnavailable);
            return;
        }

        // 생성 전에 PROCESSING을 찍는다 — 이 사이에 사용자가 상세를 열면 "요약 없음"으로 오판한다.
        updateCall(callId, Call::startSummary);
        try {
            String topic = aiServerClient
                    .summarizeCallTopic(new AiCallTopicRequest(callId, messages, MAX_TOPIC_CHARACTERS))
                    .topic()
                    .strip();
            updateCall(callId, call -> call.completeSummary(topic));
            log.info("[Summary] 요약 완료. callId={}, messages={}, topic={}, totalMs={}",
                    callId, messages.size(), topic, elapsedMs(startedAt));
        } catch (RuntimeException e) {
            log.error("[Summary] 요약 실패. callId={}, messages={}, totalMs={}",
                    callId, messages.size(), elapsedMs(startedAt), e);
            updateCall(callId, Call::failSummary);
        }
    }

    /**
     * 통화 전사를 AI 요청 모양으로 읽는다. 발화 순서(id ASC) = 시간순이다.
     *
     * <p>⚠ <b>메모리에 있던 대화 로그가 아니라 DB를 읽는다.</b> 그쪽은 통화 워커 스레드에 confine된
     * 가변 리스트라 다른 스레드에서 읽으면 데이터 레이스다.
     *
     * <p>⚠ <b>마지막 턴이 빠질 수 있다.</b> 정리 경로가 워커를 {@code shutdownNow()}로 끊고 오므로,
     * 그 순간 송신 중이던 AI 문장은 전사에 안 남는다. 요약은 주제 라벨이라 한 문장이 빠져도 결과가
     * 크게 달라지지 않아 감수한다 — 정확히 맞추려면 워커 종료를 기다려야 하고 그만큼 끊기가 느려진다.
     */
    private List<AiSummaryMessage> loadTranscript(Long callId) {
        try {
            return callHistoryRepository.findByCallIdOrderByIdAsc(callId).stream()
                    .filter(history -> history.getContent() != null && !history.getContent().isBlank())
                    .map(history -> new AiSummaryMessage(toRole(history.getSpeaker()), history.getContent()))
                    .toList();
        } catch (RuntimeException e) {
            log.error("[Summary] 전사 조회 실패. callId={}", callId, e);
            return List.of();
        }
    }

    /** AI 계약의 role 값은 소문자 {@code user}/{@code assistant}다(채팅·통화 공통). */
    private static String toRole(CallSpeaker speaker) {
        return speaker == CallSpeaker.USER ? "user" : "assistant";
    }

    /**
     * 통화 하나의 요약 상태를 짧은 트랜잭션으로 갱신한다.
     * <p>⚠ 느린 LLM 호출을 트랜잭션 안에 두지 않으려고 {@code @Transactional} 대신 템플릿을 쓴다 —
     * 감싸면 생성 내내 DB 커넥션을 잡아 풀이 마른다.
     */
    private void updateCall(Long callId, Consumer<Call> change) {
        try {
            transactionTemplate.executeWithoutResult(status ->
                    callRepository.findById(callId).ifPresent(change));
        } catch (RuntimeException e) {
            log.error("[Summary] 요약 상태 저장 실패. callId={}", callId, e);
        }
    }

    /**
     * 기동 시 남아 있는 {@code PROCESSING}을 전부 {@code FAILED}로 내린다.
     * <p>생성은 앱이 살아 있을 때만 도므로 <b>기동 시점의 {@code PROCESSING}은 정의상 전부 죽은 것</b>이다.
     * 안 걷으면 그 통화는 프론트에서 영영 "준비 중"으로 남는다.
     * <p>⚠ 단일 인스턴스 전제다 — 다중화하면 남의 인스턴스가 만드는 중인 요약을 실패로 만든다.
     */
    @PostConstruct
    void failStaleSummaries() {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                int failed = callRepository.failStaleSummaries();
                if (failed > 0) {
                    log.warn("[Summary] 기동 전에 끊긴 요약 생성 {}건을 FAILED로 마감했다.", failed);
                }
            });
        } catch (RuntimeException e) {
            log.error("[Summary] 끊긴 요약 마감 실패(무시).", e);
        }
    }

    /** 앱 종료 시 진행 중인 생성을 잠시 기다린다 — 배포 때마다 그 순간 끝난 통화의 요약을 잃지 않도록. */
    @PreDestroy
    void drainSummaries() {
        summarizer.shutdown();
        try {
            if (!summarizer.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
                log.warn("[Summary] 요약 생성이 끝나지 않아 포기한다.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 경과 시간(ms). 벽시계가 아니라 단조 시계라 시각 보정에 흔들리지 않는다. */
    private static long elapsedMs(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000L;
    }
}
