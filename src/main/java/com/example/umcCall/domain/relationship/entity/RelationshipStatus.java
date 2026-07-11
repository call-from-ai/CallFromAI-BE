package com.example.umcCall.domain.relationship.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 회원-캐릭터 간 통화/채팅 누적 통계. 카운트 증가 로직은 채팅/통화 도메인에서 처리한다.
 * 관계 시작일(startedAt)은 relationship 쪽 값을 참조해서 쓰고 여기서 중복 저장하지 않는다.
 */
@Entity
@Getter
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RelationshipStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "relationship_status_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "relationship_id")
    private Relationship relationship;

    private Integer chatMessageCount;

    private Integer totalCallCount;

    private Integer callStreakDays;

    // 통화 이력이 없으면 null
    private LocalDateTime lastCallAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Builder
    public RelationshipStatus(Relationship relationship) {
        this.relationship = relationship;
        this.chatMessageCount = 0;
        this.totalCallCount = 0;
        this.callStreakDays = 0;
    }
}
