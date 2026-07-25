package com.example.umcCall.domain.call.entity;

import com.example.umcCall.domain.call.enums.CallReservationStatus;
import com.example.umcCall.domain.relationship.entity.Relationship;
import com.example.umcCall.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name="call_reservation",
        indexes = @Index(name = "idx_reservation_status_scheduled_at",
                columnList = "status, scheduled_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CallReservation extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="call_reservation_id")
    private Long id;

    @Column(name="scheduled_at",nullable = false)
    private LocalDateTime scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(name="status",nullable = false)
    private CallReservationStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="relationship_id",nullable = false)
    private Relationship relationship;

    @Builder
    private CallReservation(Relationship relationship, LocalDateTime scheduledAt) {
        this.relationship = relationship;
        this.scheduledAt = scheduledAt;
        this.status = CallReservationStatus.SCHEDULED;
    }

    /**
     * 예약 시각이 도달해 발신(Call 생성)까지 마쳤음. SCHEDULED → FIRED.
     * <p>여러 인스턴스·중복 tick이 같은 예약을 집으면 두 번째부터 여기서 막힌다 — 예약 하나는 한 번만 울린다.
     */
    public void markFired() {
        requireScheduled("발신");
        this.status = CallReservationStatus.FIRED;
    }

    /**
     * 발신하지 않고 예약을 종결. SCHEDULED → CANCELED.
     * <p>쓰이는 경우: grace window를 넘겨 뒤늦게 발견된 예약(새벽 예약이 아침에 울리는 것 방지),
     * 관계가 더는 유효하지 않음(캐릭터 삭제·메인 아님), 이미 통화가 진행 중.
     */
    public void cancel() {
        requireScheduled("취소");
        this.status = CallReservationStatus.CANCELED;
    }

    private void requireScheduled(String action) {
        if (status != CallReservationStatus.SCHEDULED) {
            throw new IllegalStateException(
                    "대기 중인 예약만 " + action + "할 수 있습니다. 현재 상태=" + status);
        }
    }
}
