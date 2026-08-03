package com.example.umcCall.domain.call.service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 마감된 통화의 산출물(녹음 업로드 · 요약 생성)을 <b>나중에 온 요청이 기다릴 수 있게</b> 하는 등록소.
 *
 * <p><b>왜 필요한가</b>: 통화는 여러 경로로 끝나는데(끊기 버튼 · 시간 상한 스위퍼 · 소켓 끊김 · 서버 오류 ·
 * 앱 종료), 산출물을 기다려 줄 요청 스레드가 있는 건 <b>끊기 버튼 경로뿐</b>이다. 나머지 경로로 끝난 통화는
 * 프론트가 종료 화면에서 {@code PROCESSING}만 받고 폴링을 돌아야 했다.
 * <p>여기에 future를 등록해 두면 <b>통화가 어떻게 끝났는지와 무관하게</b> 프론트의 상세 조회
 * ({@code GET /calls/{callId}?wait=true})가 그 future에 붙어 기다렸다가 준비된 값을 받는다.
 * 통화 소켓이 이미 죽었어도 상관없다 — 기다리는 주체가 소켓이 아니라 <b>새 HTTP 요청</b>이기 때문이다.
 *
 * <p>⚠ <b>대기 여부는 {@code summaryStatus}가 아니라 future 존재로 판정한다.</b> 통화가 막 끝난 시점엔
 * 상태가 아직 {@code NONE}이다({@code startSummary()}는 전사를 읽은 <b>뒤에</b> {@code PROCESSING}을 찍는다).
 * 상태로 판정하면 <b>가장 기다려야 할 순간에 그냥 지나간다</b>.
 *
 * <p>⚠ <b>대기 시간은 서버가 소유한다</b> — 클라이언트는 "기다릴지" 여부만 보낸다. 클라이언트가 ms를
 * 지정하게 두면 요청 스레드를 원하는 만큼 붙잡을 수 있다.
 *
 * <p>⚠ 인메모리라 <b>단일 인스턴스 전제</b>다({@code WsTicketStore}와 같은 제약). 다중화하면 남의
 * 인스턴스가 만드는 중인 산출물은 기다릴 수 없어 그 통화만 {@code PROCESSING}으로 응답된다.
 */
@Slf4j
@Component
public class CallArtifactRegistry {

    /** callId → 진행 중인 산출물 future. 완료되면 스스로 빠지므로 크기는 "지금 끝나는 중인 통화 수"에 묶인다. */
    private final ConcurrentHashMap<Long, CompletableFuture<Void>> pending = new ConcurrentHashMap<>();

    /**
     * 한 요청이 산출물을 기다리는 상한(ms).
     * <p>⚠ 상한이 없으면 S3나 AI 서버가 hang할 때 <b>요청 스레드가 영영 묶인다</b>. 넘겨도 실패가 아니라
     * 현재 상태({@code PROCESSING})로 응답할 뿐이고, 산출물은 백그라운드에서 계속 만들어진다.
     */
    private final long waitMs;

    public CallArtifactRegistry(@Value("${call.artifact.wait-ms}") long waitMs) {
        this.waitMs = waitMs;
    }

    /**
     * 통화 하나의 산출물 future를 등록한다. <b>모든 종료 경로가 지나는 지점</b>에서 불려야 한다 —
     * 그래야 "어떤 경로로 끝났든 기다릴 수 있다"가 성립한다.
     *
     * <p>완료되면 스스로 빠진다({@code whenComplete}). 녹음·요약 둘 다 fail-open이라 future는 반드시
     * 완료되므로 엔트리가 새지 않는다.
     */
    public void register(Long callId, CompletableFuture<Void> artifacts) {
        if (artifacts.isDone()) {
            return; // 만들 산출물이 없었던 통화(미연결 등). 기다릴 대상이 아니다.
        }
        pending.put(callId, artifacts);
        // remove(key, value)로 지운다 — 혹시 같은 callId에 새 future가 등록됐다면 그걸 지우지 않도록.
        artifacts.whenComplete((result, error) -> pending.remove(callId, artifacts));
    }

    /**
     * 이 통화의 산출물이 준비될 때까지 상한까지 기다린다.
     *
     * <p>⚠ <b>트랜잭션 밖에서 불러야 한다.</b> 트랜잭션 안에서 기다리면 대기 시간 내내 DB 커넥션을 잡아
     * 커넥션 풀이 마른다(동시에 끝난 통화가 몇 건만 겹쳐도 앱 전체가 멈춘다).
     *
     * <p>상한을 넘겨도 <b>취소하지 않는다</b> — 기다리기를 포기할 뿐 산출물은 계속 만들어진다.
     *
     * @return 실제로 기다렸으면 {@code true}(= 호출부가 값을 <b>다시 읽어야</b> 한다).
     *         기다릴 대상이 없었으면 {@code false} — 이미 끝났거나 산출물이 없는 통화라 재조회가 무의미하다.
     */
    public boolean await(Long callId) {
        CompletableFuture<Void> artifacts = pending.get(callId);
        if (artifacts == null) {
            return false;
        }
        long startedAt = System.nanoTime();
        try {
            artifacts.get(waitMs, TimeUnit.MILLISECONDS);
            log.info("[Call] 산출물 대기 완료. callId={}, waitedMs={}", callId, elapsedMs(startedAt));
        } catch (TimeoutException e) {
            log.info("[Call] 산출물 대기 상한({}ms) 초과 → 현재 상태로 응답한다. callId={}", waitMs, callId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            // 녹음·요약이 이미 fail-open으로 삼키므로 여기까지 오면 예상 밖이다. 조회는 그대로 성공시킨다.
            log.error("[Call] 산출물 대기 중 예상치 못한 실패(조회는 계속). callId={}", callId, e);
        }
        return true;
    }

    /** 경과 시간(ms). 벽시계가 아니라 단조 시계라 시각 보정에 흔들리지 않는다. */
    private static long elapsedMs(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000L;
    }
}
