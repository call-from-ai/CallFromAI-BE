package com.example.umcCall.domain.call.entity;

import com.example.umcCall.domain.call.enums.CallSender;
import com.example.umcCall.domain.call.enums.CallStatus;
import com.example.umcCall.domain.relationship.entity.Relationship;
import com.example.umcCall.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Duration;
import java.time.LocalDateTime;

@Entity
@Table(name="calls")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Call extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="call_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name="sender",nullable = false)
    private CallSender sender;

    @Enumerated(EnumType.STRING)
    @Column(name="status",nullable = false)
    private CallStatus status;

    @Column(name="ai_summary", length = 150)
    private String aiSummary;

    @Column(name="audio_url")
    private String audioUrl;

    @Column(name="call_time")
    private Integer callTime;

    @Column(name="started_at")
    private LocalDateTime startedAt;

    @Column(name="ended_at")
    private LocalDateTime endedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="relationship_id",nullable = false)
    private Relationship relationship;

    @Column(name="call_reservation_id")
    private Long callReservationId;

    @Builder
    private Call(Relationship relationship, CallSender sender) {
        this.relationship = relationship;
        this.sender = sender;
        this.status = (sender == CallSender.USER) ?
                CallStatus.DIALING : CallStatus.RINGING;
    }

    /** 통화 연결됨. 연결 대기 중(DIALING 또는 RINGING)에서만 IN_PROGRESS로 전이한다. */
    public void connect() {
        if (status != CallStatus.DIALING && status != CallStatus.RINGING) {
            throw new IllegalStateException("연결 대기 중인 통화만 연결할 수 있습니다. 현재 상태=" + status);
        }
        this.status = CallStatus.IN_PROGRESS;
        this.startedAt = LocalDateTime.now();
    }

    /**
     * 통화 정상 종료. 연결된 통화(IN_PROGRESS)에서만 유효하다.
     */
    public void complete() {
        if (status != CallStatus.IN_PROGRESS) {
            throw new IllegalStateException("연결된 통화만 완료할 수 있습니다. 현재 상태=" + status);
        }
        this.status = CallStatus.COMPLETED;
        this.endedAt = LocalDateTime.now();
        this.callTime = (int) Duration.between(startedAt, endedAt).getSeconds();
    }

    /**
     * 연결되지 못한 채 서버/AI 측 사유로 취소됨(스트림 개설 실패 등). → CANCELED.
     * <p>사용자 취소가 아니다 — 연결 전 사용자 취소는 취소 API가 없어 현재 도달 불가다.
     */
    public void cancel() {
        this.status = CallStatus.CANCELED;
    }

    /**
     * 사용자가 착신을 거절함(AI 발신). RINGING → REJECTED.
     * <p>API로 직접 호출되는 전이라 상태 검증을 엔티티가 직접 한다.
     */
    public void reject() {
        if (status != CallStatus.RINGING) {
            throw new IllegalStateException("착신 대기 중인 통화만 거절할 수 있습니다. 현재 상태=" + status);
        }
        this.status = CallStatus.REJECTED;
    }
}
