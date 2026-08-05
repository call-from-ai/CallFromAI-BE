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
 * <p><b>업로드는 비동기다.</b> 정리 경로를 부르는 스레드가 사용자 REST 요청 스레드일 수 있어
 * ({@code PATCH /calls/{id}/end}의 {@code AFTER_COMMIT} 리스너), 여기서 수 초를 잡으면
 * <b>전화를 끊는 응답이 그만큼 늦어진다</b>.
 *
 * <p><b>fail-open이다.</b> 업로드가 실패해도 예외를 밖으로 내보내지 않는다 — 통화는 이미 끝났고,
 * 녹음이 없다고 통화·전사가 달라지지 않는다. 실패는 {@code FAILED}로 남겨 프론트가 "준비 중"에
 * 갇히지 않게 한다.
 *
 * <p>⚠ 녹음은 <b>채팅 사진과 같은 수준</b>으로 공개된다(퍼블릭 버킷 + UUID 파일명). 서비스 오픈 전에
 * 사진·녹음을 함께 비공개로 전환할 것 — 그때는 저장값이 URL이 아니라 key가 되고 조회 시 presign한다.
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
     * <p>⚠ <b>단일 스레드였다가 풀로 바꿨다.</b> 예전 근거는 "통화가 끝난 뒤의 뒷일이라 순차로 충분하다"였는데,
     * 이제 <b>종료 응답이 이 결과를 기다리므로</b> 그 근거가 무너졌다 — 동시에 끝난 통화들이 줄을 서면
     * 뒤 통화의 <b>끊기 응답이 앞 통화의 업로드 시간만큼 밀린다</b>. S3 대기는 CPU가 아니라 I/O라
     * 몇 개 더 두는 비용이 거의 없다. (줄 서는 정도는 {@code queueMs} 로그로 관측된다)
     */
    private final ExecutorService uploader = Executors.newFixedThreadPool(4);

    /**
     * 마감된 녹음 하나를 보관 대기열에 넣는다.
     *
     * <p>⚠ 호출 스레드(WS 수신 · 사용자 종료 REST)에서 도는 건 <b>상태 갱신 UPDATE 하나뿐</b>이다 —
     * 느린 S3 호출은 풀로 넘긴다. 정리 경로를 잡아두지 않는다는 규칙은 그대로고, 이 UPDATE는
     * 그 규칙이 막으려던 "수 초짜리 대기"가 아니다.
     *
     * @return 업로드가 끝나면(성공·실패 무관) 완료되는 future. 통화 종료 응답이 상한까지만 이걸 기다린다 —
     *         상한을 넘겨도 <b>취소하지 않는다</b>(기다리기를 포기할 뿐 업로드는 계속된다).
     */
    public CompletableFuture<Void> save(Long callId, byte[] wav) {
        long queuedAt = System.nanoTime();
        // ⚠ 큐에 "넣기 전에" 찍는다 — 풀(4)이 바쁘면 제출과 실행 사이가 벌어지는데, 그동안 상태가
        // NONE이면 프론트가 "녹음 없음"으로 오판한다. 하필 조회 대기(?wait=true)가 상한을 넘긴
        // 순간이 가장 위험하다 — 실제로는 업로드 대기 중인데 "녹음 없음"으로 확정해 버린다.
        updateCall(callId, Call::startRecordingUpload);
        try {
            return CompletableFuture.runAsync(() -> upload(callId, wav, queuedAt), uploader);
        } catch (RejectedExecutionException e) {
            // 앱 종료로 업로더가 내려간 뒤. ⚠ 녹음이 "없는" 게 아니라 "보관하지 못한" 것이라 FAILED다.
            // 이 UPDATE 자체가 실패해도(종료 중이라 DB가 먼저 닫혔을 수 있다) 상태는 PROCESSING으로
            // 남고 다음 기동의 failStaleUploads가 걷는다 — 어느 쪽이든 "준비 중"에 갇히지 않는다.
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
     * 녹음 보관에 걸린 시간을 한 줄로 남긴다. <b>측정 목적이 분명하다</b>: 이 업로드를 통화 종료 응답
     * ({@code PATCH /calls/{id}/end}의 AFTER_COMMIT) 안에서 <b>기다릴지</b>, 기다린다면 상한을 몇 초로 둘지
     * 정하는 근거다(#129). 지우거나 레벨을 낮추면 그 판단 근거가 사라진다.
     *
     * <p>값의 의미가 서로 다르니 묶어 읽지 말 것:
     * <ul>
     *   <li>{@code queueMs} — 업로더 큐 대기. ⚠ <b>동기로 바꾸면 사라지는 값</b>이라 상한 계산에서 빼야 한다.
     *       여기가 크면 통화가 몰려 단일 스레드 업로더가 밀린다는 뜻이다.</li>
     *   <li>{@code uploadMs} — S3 호출만. <b>상한을 정하는 실제 숫자</b>.</li>
     *   <li>{@code totalMs} — 큐 + 상태 갱신 tx 2개 + 업로드. 현재 구조에서 프론트가 겪는 지연.</li>
     * </ul>
     * {@code bytes}를 같이 남기는 건 통화 길이에 비례하는지 보기 위해서다(5분 상한 = 최대 ~9.6MB).
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
