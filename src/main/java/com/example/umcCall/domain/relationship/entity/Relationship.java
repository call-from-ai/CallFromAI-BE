package com.example.umcCall.domain.relationship.entity;

import com.example.umcCall.domain.character.entity.Character;
import com.example.umcCall.domain.character.enums.SpeechStyle;
import com.example.umcCall.domain.relationship.enums.RelationshipStage;
import com.example.umcCall.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Version;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원-캐릭터 간 관계 정보. 관계 단계·활성 여부·연애 온도·말투는 캐릭터가 아니라 여기서 관리한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Relationship extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "relationship_id")
    private Long id;

    private Long memberId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "character_id")
    private Character character;

    @Enumerated(EnumType.STRING)
    private RelationshipStage relationshipStage;

    // 관계 온도(AI relationshipTemperatureScore) baseline. 항상 0~100 범위를 보장한다.
    @Column(nullable = false)
    @Min(0)
    @Max(100)
    private Integer affinityScore;

    private Integer floorScore;

    private String emotion;

    private Integer spiceLevel;

    @Enumerated(EnumType.STRING)
    private SpeechStyle speechStyle;

    private LocalDate startedAt;

    // 회원당 메인(활성) 관계는 동시에 하나만 존재
    @Column(name = "is_main")
    private boolean main;

    private LocalDateTime becameMainAt;

    @Version
    private Long version;

    @Builder
    public Relationship(Long memberId, Character character, RelationshipStage relationshipStage,
                         Integer spiceLevel, SpeechStyle speechStyle, boolean main) {
        this.memberId = memberId;
        this.character = character;
        this.relationshipStage = relationshipStage;
        this.affinityScore = 50;
        this.spiceLevel = spiceLevel;
        this.speechStyle = speechStyle;
        this.startedAt = LocalDate.now();
        this.main = main;
        this.becameMainAt = null;   // 최초 생성 시 자동 메인 지정은 3일 제한 대상 아님 (명시적 activate() 호출 시에만 세팅)
    }

    public void updateInfo(RelationshipStage relationshipStage, Integer spiceLevel, SpeechStyle speechStyle) {
        this.relationshipStage = relationshipStage;
        this.spiceLevel = spiceLevel;
        this.speechStyle = speechStyle;
    }

    public void activate() {
        this.main = true;
        this.becameMainAt = LocalDateTime.now();
    }

    public void deactivate() {
        this.main = false;
    }
}
