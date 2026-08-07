package com.example.umcCall.domain.call.recording;

import com.example.umcCall.domain.call.entity.Call;
import com.example.umcCall.domain.call.repository.CallRepository;
import com.example.umcCall.global.infra.s3.S3Uploader;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
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
 * 마감된 통화 녹음을 S3에 올리고 {@code Call.audioUrl}·{@code recordingStatus}에 결과를 남긴다.
 *
 * <p><b>업로드는 비동기다.</b> 정리 경로를 부르는 스레드가 WS 수신 스레드이거나 사용자 REST 요청
 * 스레드({@code PATCH /calls/{id}/end}의 {@code AFTER_COMMIT} 리스너)라, 여기서 수 초를 잡으면
 * 각각 오디오 수신이 멈추거나 전화를 끊는 응답이 늦어진다.
 *
 * <p><b>fail-open이다.</b> 업로드가 실패해도 예외를 밖으로 내보내지 않는다 — 통화는 이미 끝났고,
 * 녹음이 없다고 통화·전사가 달라지지 않는다. 실패는 {@code FAILED}로 남겨 프론트가 "준비 중"에
 * 갇히지 않게 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CallRecordingService {

    private static final String RECORDING_DIR = "call-recordings";
    private static final String EXTENSION = ".wav";
    private static final String CONTENT_TYPE = "audio/wav";

    /** 앱 종료 시 진행 중인 업로드를 기다려 주는 시간. 넘기면 포기한다(그 녹음만 잃는다). */
    private static final long SHUTDOWN_WAIT_SECONDS = 10;

    private final S3Uploader s3Uploader;
    private final CallRepository callRepository;
    private final TransactionTemplate transactionTemplate;

    /**
     * 업로드 풀(전 통화 공유). 통화 워커는 이 시점에 이미 내려가 있어 쓸 수 없다.
     *
     * <p>⚠ <b>순차로 두면 안 된다</b> — 종료 화면의 {@code ?wait=true} 조회가 이 결과를 기다리므로,
     * 동시에 끝난 통화가 줄을 서면 뒤 통화 사용자의 화면이 앞 통화의 업로드만큼 늦게 채워진다.
     * S3 대기는 CPU가 아니라 I/O라 몇 개 더 두는 비용이 거의 없다. (줄 서는 정도는 {@code queueMs} 로그)
     */
    private final ExecutorService uploader = Executors.newFixedThreadPool(4);

    /**
     * 마감된 녹음 하나를 보관 대기열에 넣는다.
     *
     * <p>⚠ 호출 스레드(WS 수신 · 사용자 종료 REST)에서 도는 건 <b>상태 갱신 UPDATE 하나뿐</b>이다 —
     * 느린 S3 호출은 풀로 넘긴다. 정리 경로를 잡아두지 않는다는 규칙은 그대로고, 이 UPDATE는
     * 그 규칙이 막으려던 "수 초짜리 대기"가 아니다.
     *
     * @return 업로드가 끝나면(성공·실패 무관) 완료되는 future. {@code GET /calls/{id}?wait=true}가
     *         상한까지만 이걸 기다린다 — 넘겨도 <b>취소하지 않는다</b>(기다리기만 포기하고 업로드는 계속).
     */
    public CompletableFuture<Void> save(Long callId, byte[] wav) {
        long queuedAt = System.nanoTime();
        // ⚠ 큐에 "넣기 전에" 찍는다 — 풀이 바쁠 때 대기 구간이 통째로 NONE이면
        // 프론트가 "녹음 없음"으로 확정해 버린다(업로드 대기 중인데).
        updateCall(callId, Call::startRecordingUpload);
        try {
            return CompletableFuture.runAsync(() -> upload(callId, wav, queuedAt), uploader);
        } catch (RejectedExecutionException e) {
            // 앱 종료로 업로더가 내려간 뒤. ⚠ 녹음이 "없는" 게 아니라 "보관하지 못한" 것이라 FAILED다.
            // 이 UPDATE마저 실패하면 PROCESSING으로 남아 다음 기동의 failStaleUploads가 걷는다.
            log.warn("[Recording] 업로더 종료됨 → 녹음을 버린다. callId={}", callId);
            updateCall(callId, Call::failRecording);
            return CompletableFuture.completedFuture(null);
        }
    }

    private void upload(Long callId, byte[] wav, long queuedAt) {
        long queueMs = elapsedMs(queuedAt);
        try {
            long uploadStartedAt = System.nanoTime();
            String audioUrl = s3Uploader.upload(wav, RECORDING_DIR, EXTENSION, CONTENT_TYPE);
            long uploadMs = elapsedMs(uploadStartedAt);
            updateCall(callId, call -> call.completeRecording(audioUrl));
            logDuration(callId, wav.length, queueMs, uploadMs, elapsedMs(queuedAt));
        } catch (RuntimeException e) {
            log.error("[Recording] 녹음 업로드 실패. callId={}, bytes={}, queueMs={}, elapsedMs={}",
                    callId, wav.length, queueMs, elapsedMs(queuedAt), e);
            updateCall(callId, Call::failRecording);
        }
    }

    /**
     * 녹음 보관에 걸린 시간을 한 줄로 남긴다. <b>{@code call.artifact.wait-ms}(조회 대기 상한)를 정하는
     * 유일한 근거</b>라 지우거나 레벨을 낮추면 값을 다시 정할 방법이 없다.
     *
     * <p>{@code queueMs}는 큐 대기(크면 통화가 몰려 풀이 밀린다는 뜻), {@code uploadMs}는 S3 호출만
     * (<b>상한을 정하는 실제 숫자</b>), {@code totalMs}는 프론트가 겪는 지연 전체다. 묶어 읽지 말 것.
     */
    private void logDuration(Long callId, int bytes, long queueMs, long uploadMs, long totalMs) {
        log.info("[Recording] 녹음 보관 완료. callId={}, bytes={}, queueMs={}, uploadMs={}, totalMs={}",
                callId, bytes, queueMs, uploadMs, totalMs);
    }

    /** 경과 시간(ms). 벽시계가 아니라 단조 시계라 시각 보정에 흔들리지 않는다. */
    private static long elapsedMs(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000L;
    }

    /**
     * 통화 하나의 녹음 상태를 짧은 트랜잭션으로 갱신한다.
     * <p>⚠ 느린 S3 호출을 트랜잭션 안에 두지 않으려고 {@code @Transactional} 대신 템플릿을 쓴다 —
     * 감싸면 업로드 내내 DB 커넥션을 잡아 풀이 마른다.
     */
    private void updateCall(Long callId, Consumer<Call> change) {
        try {
            transactionTemplate.executeWithoutResult(status ->
                    callRepository.findById(callId).ifPresent(change));
        } catch (RuntimeException e) {
            log.error("[Recording] 녹음 상태 저장 실패. callId={}", callId, e);
        }
    }

    /**
     * 기동 시 남아 있는 {@code PROCESSING}을 전부 {@code FAILED}로 내린다.
     * <p>업로드는 앱이 살아 있을 때만 도므로 <b>기동 시점의 {@code PROCESSING}은 정의상 전부 죽은 것</b>이다
     * (직전 프로세스가 업로드 중에 죽었다는 뜻). 안 걷으면 그 통화는 프론트에서 영영 "준비 중"으로 남는다.
     * <p>⚠ 단일 인스턴스 전제다 — 다중화하면 남의 인스턴스가 올리는 중인 녹음을 실패로 만든다.
     */
    @PostConstruct
    void failStaleUploads() {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                int failed = callRepository.failStaleRecordings();
                if (failed > 0) {
                    log.warn("[Recording] 기동 전에 끊긴 업로드 {}건을 FAILED로 마감했다.", failed);
                }
            });
        } catch (RuntimeException e) {
            log.error("[Recording] 끊긴 업로드 마감 실패(무시).", e);
        }
    }

    /**
     * 앱 종료 시 진행 중인 업로드를 잠시 기다린다 — 배포 때마다 그 순간 끝난 통화의 녹음을 잃지 않도록.
     * 못 끝낸 것은 다음 기동의 {@link #failStaleUploads}가 {@code FAILED}로 정리한다.
     */
    @PreDestroy
    void drainUploads() {
        uploader.shutdown();
        try {
            if (!uploader.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
                log.warn("[Recording] 업로드가 끝나지 않아 포기한다.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
