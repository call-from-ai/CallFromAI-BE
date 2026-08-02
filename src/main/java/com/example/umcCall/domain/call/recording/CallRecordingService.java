package com.example.umcCall.domain.call.recording;

import com.example.umcCall.domain.call.entity.Call;
import com.example.umcCall.domain.call.repository.CallRepository;
import com.example.umcCall.global.infra.s3.S3Uploader;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
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
     * 업로드 전용 스레드(전 통화 공유). 통화 워커는 이 시점에 이미 내려가 있어 쓸 수 없고,
     * 통화가 끝난 뒤의 뒷일이라 순차 처리로 충분하다.
     */
    private final ExecutorService uploader = Executors.newSingleThreadExecutor();

    /** 마감된 녹음 하나를 보관 대기열에 넣는다. 호출부(WS 핸들러)를 잡아두지 않는다. */
    public void save(Long callId, byte[] wav) {
        try {
            uploader.execute(() -> upload(callId, wav));
        } catch (RejectedExecutionException e) {
            // 앱 종료로 업로더가 내려간 뒤. 상태는 NONE으로 남는다(녹음 없음과 구별할 방법이 없다).
            log.warn("[Recording] 업로더 종료됨 → 녹음을 버린다. callId={}", callId);
        }
    }

    private void upload(Long callId, byte[] wav) {
        // 올리기 전에 PROCESSING을 찍는다 — 이 사이에 사용자가 상세를 열면 "녹음 없음"으로 오판한다.
        updateCall(callId, Call::startRecordingUpload);
        try {
            String audioUrl = s3Uploader.upload(wav, RECORDING_DIR, EXTENSION, CONTENT_TYPE);
            updateCall(callId, call -> call.completeRecording(audioUrl));
            log.info("[Recording] 녹음 업로드 완료. callId={}, bytes={}", callId, wav.length);
        } catch (RuntimeException e) {
            log.error("[Recording] 녹음 업로드 실패. callId={}, bytes={}", callId, wav.length, e);
            updateCall(callId, Call::failRecording);
        }
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
