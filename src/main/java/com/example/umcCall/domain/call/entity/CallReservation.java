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
}
