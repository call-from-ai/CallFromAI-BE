package com.example.umcCall.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * AI 답장 디바운스용 스레드풀 설정.
 * 타이머(짧은 예약)와 실제 AI 처리(느린 REST 호출)를 서로 다른 풀로 분리한다.
 * 한 풀에 섞으면 느린 AI 처리가 타이머 스레드를 점유해 다른 방의 답장이 밀리기 때문이다.
 */
@Configuration
public class SchedulerConfig {

    /** 디바운스 타이머 전용. 짧은 예약만 처리하므로 작은 풀로 충분하다. */
    @Bean
    public ThreadPoolTaskScheduler aiReplyTimer() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(3);
        scheduler.setThreadNamePrefix("ai-reply-timer-");
        scheduler.setRemoveOnCancelPolicy(true);   // 취소된 타이머를 큐에서 즉시 제거(메모리 누수 방지)
        return scheduler;
    }

    /** 실제 AI 답장 처리 전용. AI 서버 호출이 수 초 걸릴 수 있어 별도 풀로 격리한다. */
    @Bean
    public ThreadPoolTaskExecutor aiReplyWorker() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("ai-reply-worker-");
        return executor;
    }
}
