package com.example.umcCall.domain.call.recording;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.umcCall.domain.call.entity.Call;
import com.example.umcCall.domain.call.enums.CallRecordingStatus;
import com.example.umcCall.domain.call.enums.CallSender;
import com.example.umcCall.domain.call.repository.CallRepository;
import com.example.umcCall.global.infra.s3.S3Uploader;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 녹음 보관 결과가 통화에 어떻게 남는지를 고정하는 테스트.
 * <p>프론트가 {@code recordingStatus}로 화면을 고르므로, 여기가 틀리면 "준비 중"에 영영 갇히거나
 * 있는 녹음을 없는 것으로 그린다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CallRecordingServiceTest {

    private static final Long CALL_ID = 7L;
    /** 풀이 꽉 찬 동안 큐에서 대기하는 <b>다른</b> 통화. */
    private static final Long QUEUED_CALL_ID = 8L;
    /** {@code CallRecordingService}의 업로드 풀 크기. 이만큼 채우면 다음 제출은 큐에서 대기만 한다. */
    private static final int UPLOADER_THREADS = 4;
    private static final byte[] WAV = new byte[] {1, 2, 3};

    @Mock private S3Uploader s3Uploader;
    @Mock private CallRepository callRepository;
    @Mock private TransactionTemplate transactionTemplate;

    private CallRecordingService service;
    private Call call;
    private Call queuedCall;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        call = Call.builder().sender(CallSender.AI).build();
        queuedCall = Call.builder().sender(CallSender.AI).build();
        when(callRepository.findById(CALL_ID)).thenReturn(Optional.of(call));
        when(callRepository.findById(QUEUED_CALL_ID)).thenReturn(Optional.of(queuedCall));
        // 트랜잭션 경계는 검증 대상이 아니라 그 자리에서 바로 실행한다.
        doAnswer(invocation -> {
            ((Consumer<TransactionStatus>) invocation.getArgument(0)).accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        service = new CallRecordingService(s3Uploader, callRepository, transactionTemplate);
    }

    @Test
    void 업로드에_성공하면_URL과_READY가_남는다() {
        when(s3Uploader.upload(any(), any(), any(), any())).thenReturn("https://bucket/call-recordings/a.wav");

        service.save(CALL_ID, WAV);

        verify(s3Uploader, timeout(2000)).upload(any(), any(), any(), any());
        assertThat(call.getAudioUrl()).isEqualTo("https://bucket/call-recordings/a.wav");
        assertThat(call.getRecordingStatus()).isEqualTo(CallRecordingStatus.READY);
    }

    @Test
    void 업로드에_실패하면_FAILED로_남는다() {
        // ⚠ NONE으로 두면 프론트가 "녹음 없음"과 구별하지 못하고, PROCESSING으로 두면 영영 "준비 중"이다.
        when(s3Uploader.upload(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("S3 장애"));

        service.save(CALL_ID, WAV);

        verify(s3Uploader, timeout(2000)).upload(any(), any(), any(), any());
        assertThat(call.getRecordingStatus()).isEqualTo(CallRecordingStatus.FAILED);
        assertThat(call.getAudioUrl()).isNull();
    }

    @Test
    void 업로드_실패가_호출부로_번지지_않는다() {
        // fail-open: 통화는 이미 끝났고, 녹음이 없다고 통화·전사가 달라지지 않는다.
        when(s3Uploader.upload(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("S3 장애"));

        assertThatCode(() -> service.save(CALL_ID, WAV)).doesNotThrowAnyException();
    }

    @Test
    void 올리기_전에_PROCESSING을_찍는다() {
        // 업로드가 도는 동안 사용자가 상세를 열면, 이 값이 없으면 "녹음 없음"으로 오판한다.
        when(s3Uploader.upload(any(), any(), any(), any())).thenAnswer(invocation -> {
            assertThat(call.getRecordingStatus()).isEqualTo(CallRecordingStatus.PROCESSING);
            return "https://bucket/call-recordings/a.wav";
        });

        service.save(CALL_ID, WAV);

        verify(s3Uploader, timeout(2000)).upload(any(), any(), any(), any());
        assertThat(call.getRecordingStatus()).isEqualTo(CallRecordingStatus.READY);
    }

    @Test
    void 큐에서_대기하는_동안에도_PROCESSING이다() throws InterruptedException {
        // ⚠ PROCESSING을 업로드 작업 "안"에서 찍으면 풀이 바쁠 때 큐 대기 구간이 통째로 NONE이라,
        // 프론트는 실제로는 보관 대기 중인 녹음을 "녹음 없음"으로 확정해 버린다.
        CountDownLatch started = new CountDownLatch(UPLOADER_THREADS);
        CountDownLatch release = new CountDownLatch(1);
        when(s3Uploader.upload(any(), any(), any(), any())).thenAnswer(invocation -> {
            started.countDown();
            release.await();
            return "https://bucket/call-recordings/a.wav";
        });

        try {
            for (int i = 0; i < UPLOADER_THREADS; i++) {
                service.save(CALL_ID, WAV);
            }
            assertThat(started.await(2, TimeUnit.SECONDS)).as("풀이 꽉 찼다").isTrue();

            // 이 통화의 업로드 작업은 큐에 들어가기만 하고 한 번도 실행되지 않는다.
            service.save(QUEUED_CALL_ID, WAV);

            assertThat(queuedCall.getRecordingStatus()).isEqualTo(CallRecordingStatus.PROCESSING);
        } finally {
            release.countDown();
        }
    }

    @Test
    void 풀이_내려간_뒤의_제출은_NONE이_아니라_FAILED다() {
        // 앱 종료로 업로더가 내려간 뒤. 녹음이 "없는" 게 아니라 "보관하지 못한" 것이다.
        service.drainUploads();

        assertThatCode(() -> service.save(CALL_ID, WAV)).doesNotThrowAnyException();

        verify(s3Uploader, never()).upload(any(), any(), any(), any());
        assertThat(call.getRecordingStatus()).isEqualTo(CallRecordingStatus.FAILED);
    }

    @Test
    void 기동_시_끊긴_업로드는_실패로_마감한다() {
        // 업로드는 앱이 살아 있을 때만 도므로, 기동 시점의 PROCESSING은 정의상 전부 죽은 것이다.
        lenient().when(callRepository.failStaleRecordings()).thenReturn(2);

        service.failStaleUploads();

        verify(callRepository).failStaleRecordings();
    }
}
