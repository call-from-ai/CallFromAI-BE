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

    /** 통화 연결됨. DIALING 또는 RINGING → IN_PROGRESS */
    public void connect() {
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
     * 발신자가 연결 전에 끊음. DIALING → CANCELED.
     */
    public void cancel() {
        this.status = CallStatus.CANCELED;
    }
}
