package com.example.umcCall.domain.relationship.entity;

import com.example.umcCall.global.entity.BaseTimeEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * 회원-캐릭터 간 통화/채팅 누적 통계. 카운트 증가 로직은 채팅/통화 도메인에서 처리한다.
 */
@Entity
@Getter
// ERD 컬럼명(started_at)에 맞추기 위해 상속받은 createdAt의 매핑 컬럼명을 재정의한다.
@AttributeOverride(name = "createdAt", column = @Column(name = "started_at"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RelationshipStatus extends BaseTimeEntity {

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

    @Builder
    public RelationshipStatus(Relationship relationship) {
        this.relationship = relationship;
        this.chatMessageCount = 0;
        this.totalCallCount = 0;
        this.callStreakDays = 0;
    }
}
