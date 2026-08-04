package com.example.umcCall.domain.call.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * 산출물 대기 등록소의 계약 테스트.
 *
 * <p>여기가 깨지면 프론트의 "종료 화면에서 조회 한 번"이 깨진다 — 안 기다리고 지나가면
 * {@code PROCESSING}이 내려가 폴링이 되살아나고, 안 풀리면 요청 스레드가 묶인다.
 */
class CallArtifactRegistryTest {

    private static final Long CALL_ID = 7L;

    /** 대기 상한. 테스트가 실제로 이만큼 기다리는 경우가 있어 짧게 둔다. */
    private static final long WAIT_MS = 200;

    @Test
    void 기다릴_대상이_없으면_즉시_돌아온다() {
        // 산출물이 없는 통화(미연결·거절 등)나 이미 끝난 통화. 여기서 붙잡으면 상세 조회가 통째로 느려진다.
        CallArtifactRegistry registry = new CallArtifactRegistry(WAIT_MS);

        long startedAt = System.nanoTime();
        boolean waited = registry.await(CALL_ID);

        assertThat(waited).isFalse();
        assertThat(elapsedMs(startedAt)).isLessThan(WAIT_MS);
    }

    @Test
    void 등록된_산출물이_끝날_때까지_기다렸다가_돌아온다() throws Exception {
        // 이게 기능의 전부다 — 통화가 어떤 경로로 끝났든 이 future에 붙어 기다린다.
        CallArtifactRegistry registry = new CallArtifactRegistry(5000);
        CompletableFuture<Void> artifacts = new CompletableFuture<>();
        registry.register(CALL_ID, artifacts);

        CompletableFuture<Boolean> awaited = CompletableFuture.supplyAsync(() -> registry.await(CALL_ID));

        // 아직 안 끝났으니 조회 스레드는 붙잡혀 있어야 한다.
        Thread.sleep(50);
        assertThat(awaited).isNotDone();

        artifacts.complete(null);
        assertThat(awaited.get(2, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void 상한을_넘겨도_예외없이_돌아온다() {
        // ⚠ 상한이 없으면 S3·AI가 hang할 때 요청 스레드가 영영 묶인다.
        // 넘겨도 실패가 아니다 — 그 시점 상태(PROCESSING)로 응답하고 산출물은 백그라운드에서 계속된다.
        CallArtifactRegistry registry = new CallArtifactRegistry(WAIT_MS);
        registry.register(CALL_ID, new CompletableFuture<>()); // 영원히 안 끝난다

        long startedAt = System.nanoTime();
        boolean waited = registry.await(CALL_ID);
        long elapsedMs = elapsedMs(startedAt);

        assertThat(waited).isTrue();
        assertThat(elapsedMs).isGreaterThanOrEqualTo(WAIT_MS);
        assertThat(elapsedMs).isLessThan(WAIT_MS * 5);
    }

    @Test
    void 끝난_산출물은_등록소에서_빠진다() {
        // 안 빠지면 맵이 통화 수만큼 계속 자란다(누수).
        CallArtifactRegistry registry = new CallArtifactRegistry(WAIT_MS);
        CompletableFuture<Void> artifacts = new CompletableFuture<>();
        registry.register(CALL_ID, artifacts);
        artifacts.complete(null);

        // 이미 끝났으니 기다릴 대상이 아니다 = 호출부도 재조회하지 않는다.
        assertThat(registry.await(CALL_ID)).isFalse();
    }

    @Test
    void 이미_끝난_산출물은_등록하지_않는다() {
        // 만들 산출물이 없었던 통화(무음·미연결)는 등록할 이유가 없다.
        CallArtifactRegistry registry = new CallArtifactRegistry(WAIT_MS);

        registry.register(CALL_ID, CompletableFuture.completedFuture(null));

        assertThat(registry.await(CALL_ID)).isFalse();
    }

    @Test
    void 통화마다_따로_기다린다() {
        // 한 통화의 산출물이 다른 통화의 조회를 붙잡으면 안 된다.
        CallArtifactRegistry registry = new CallArtifactRegistry(WAIT_MS);
        registry.register(CALL_ID, new CompletableFuture<>());

        long startedAt = System.nanoTime();
        boolean waited = registry.await(CALL_ID + 1);

        assertThat(waited).isFalse();
        assertThat(elapsedMs(startedAt)).isLessThan(WAIT_MS);
    }

    private static long elapsedMs(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000L;
    }
}
