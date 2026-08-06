package com.example.umcCall.domain.proactive.entity;

import com.example.umcCall.domain.proactive.enums.ProactiveAction;
import com.example.umcCall.domain.relationship.entity.Relationship;
import com.example.umcCall.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(
        name = "proactive_contact_schedule",
        indexes = {
                @Index(name = "idx_proactive_due", columnList = "enabled,next_check_at"),
                @Index(name = "idx_proactive_retry", columnList = "pending_retry_at")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProactiveContactSchedule extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "proactive_contact_schedule_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "relationship_id", nullable = false, unique = true)
    private Relationship relationship;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "next_check_at")
    private LocalDateTime nextCheckAt;

    @Column(name = "last_proactive_contact_at")
    private LocalDateTime lastProactiveContactAt;

    @Column(name = "consecutive_no_response_count", nullable = false)
    private int consecutiveNoResponseCount;

    @Column(name = "awaiting_user_response", nullable = false)
    private boolean awaitingUserResponse;

    @Column(name = "daily_contact_count", nullable = false)
    private int dailyContactCount;

    @Column(name = "daily_count_date")
    private LocalDate dailyCountDate;

    @Column(name = "daily_call_count", nullable = false)
    private int dailyCallCount;

    @Column(name = "paused_until")
    private LocalDateTime pausedUntil;

    @Column(name = "pending_request_id", unique = true, length = 80)
    private String pendingRequestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "pending_action")
    private ProactiveAction pendingAction;

    @Column(name = "pending_contact_reason", length = 80)
    private String pendingContactReason;

    @Column(name = "pending_attempts", nullable = false)
    private int pendingAttempts;

    @Column(name = "pending_retry_at")
    private LocalDateTime pendingRetryAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Version
    private Long version;

    private ProactiveContactSchedule(Relationship relationship, LocalDateTime nextCheckAt) {
        this.relationship = relationship;
        this.enabled = true;
        this.nextCheckAt = nextCheckAt;
        this.consecutiveNoResponseCount = 0;
        this.dailyContactCount = 0;
        this.dailyCallCount = 0;
        this.pendingAttempts = 0;
    }

    public static ProactiveContactSchedule create(Relationship relationship, LocalDateTime nextCheckAt) {
        return new ProactiveContactSchedule(relationship, nextCheckAt);
    }

    public void reschedule(LocalDateTime nextCheckAt) {
        clearPending();
        this.pausedUntil = null;
        this.nextCheckAt = enabled ? nextCheckAt : null;
    }

    public void deferUntil(LocalDateTime nextCheckAt) {
        this.nextCheckAt = nextCheckAt;
    }

    public void forceDue(LocalDateTime now) {
        clearPending();
        this.pausedUntil = null;
        this.nextCheckAt = now;
    }

    public void claim(String requestId, ProactiveAction action, String contactReason) {
        this.pendingRequestId = requestId;
        this.pendingAction = action;
        this.pendingContactReason = contactReason;
        this.pendingAttempts = 0;
        this.pendingRetryAt = LocalDateTime.now();
        this.nextCheckAt = null;
        this.lastError = null;
    }

    public void complete(LocalDateTime sentAt, LocalDateTime nextCheckAt) {
        resetDailyCountIfNeeded(sentAt.toLocalDate());
        this.dailyContactCount++;
        this.lastProactiveContactAt = sentAt;
        this.awaitingUserResponse = true;
        clearPending();
        this.nextCheckAt = enabled ? nextCheckAt : null;
    }

    public void completeCall(LocalDateTime calledAt, LocalDateTime nextCheckAt) {
        resetDailyCountIfNeeded(calledAt.toLocalDate());
        this.dailyContactCount++;
        this.dailyCallCount++;
        this.lastProactiveContactAt = calledAt;
        this.awaitingUserResponse = false;
        clearPending();
        this.nextCheckAt = enabled ? nextCheckAt : null;
    }

    public void releaseClaim(LocalDateTime nextCheckAt) {
        clearPending();
        this.nextCheckAt = enabled ? nextCheckAt : null;
    }

    /**
     * 테스트 발송이 운영 스케줄 상태에 영향을 주지 않도록 임시 claim만 제거하고 원래 상태를 복원한다.
     */
    public void completeDebug(LocalDateTime previousNextCheckAt, String previousLastError) {
        clearPending();
        this.nextCheckAt = enabled ? previousNextCheckAt : null;
        this.lastError = previousLastError;
    }

    public void retry(RuntimeException exception, LocalDateTime now) {
        pendingAttempts++;
        long delayMinutes = Math.min(60, 1L << Math.min(pendingAttempts - 1, 6));
        pendingRetryAt = now.plusMinutes(delayMinutes);
        String message = exception.getMessage();
        lastError = message == null ? exception.getClass().getSimpleName()
                : message.substring(0, Math.min(message.length(), 1000));
    }

    public void retryWithNewRequest(LocalDateTime now) {
        clearPending();
        this.nextCheckAt = enabled ? now : null;
    }

    public void recordNoResponse() {
        if (!awaitingUserResponse) return;
        consecutiveNoResponseCount++;
        awaitingUserResponse = false;
        if (consecutiveNoResponseCount >= 3) this.nextCheckAt = null;
    }

    public void recordUserResponse(LocalDateTime nextCheckAt) {
        consecutiveNoResponseCount = 0;
        awaitingUserResponse = false;
        pausedUntil = null;
        clearPending();
        this.nextCheckAt = enabled ? nextCheckAt : null;
    }

    public void pauseUntil(LocalDateTime pausedUntil) {
        this.pausedUntil = pausedUntil;
        this.nextCheckAt = pausedUntil;
    }

    public void disable() {
        enabled = false;
        nextCheckAt = null;
        clearPending();
    }

    public void enable(LocalDateTime nextCheckAt) {
        enabled = true;
        this.nextCheckAt = nextCheckAt;
    }

    public int dailyCountOn(LocalDate date) {
        return date.equals(dailyCountDate) ? dailyContactCount : 0;
    }

    public int dailyCallCountOn(LocalDate date) {
        return date.equals(dailyCountDate) ? dailyCallCount : 0;
    }

    private void resetDailyCountIfNeeded(LocalDate date) {
        if (!date.equals(dailyCountDate)) {
            dailyCountDate = date;
            dailyContactCount = 0;
            dailyCallCount = 0;
        }
    }

    private void clearPending() {
        pendingRequestId = null;
        pendingAction = null;
        pendingContactReason = null;
        pendingAttempts = 0;
        pendingRetryAt = null;
        lastError = null;
    }
}
