package com.example.umcCall.domain.ai.mapper;

import com.example.umcCall.domain.ai.dto.AiRelationshipSnapshot;
import com.example.umcCall.domain.relationship.entity.Relationship;
import com.example.umcCall.domain.relationship.entity.RelationshipStatus;
import com.example.umcCall.domain.relationship.enums.RelationshipStage;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;

@Component
public class AiRelationshipSnapshotMapper {

    /** 우리 DB엔 대응 컬럼이 없는 점수의 신규 시드값. AI 서버는 관계 점수를 stateless round-trip으로 다룬다. */
    private static final int SEED_TRUST = 50;
    private static final int SEED_REPAIR_PROGRESS = 0;
    private static final int SEED_BREAKUP_RISK = 0;
    private static final String DEFAULT_STRATEGY = "NORMAL";

    public AiRelationshipSnapshot toSnapshot(
            Relationship relationship,
            RelationshipStatus relationshipStatus
    ) {
        LocalDate startedAt = relationshipStatus.getCreatedAt().toLocalDate();
        long daysTogether = Math.max(0, ChronoUnit.DAYS.between(startedAt, LocalDate.now()));

        return new AiRelationshipSnapshot(
                relationship.getId(),
                mapStage(relationship.getRelationshipStage()),
                relationship.getAffinityScore(), // 관계 온도 = 호감도. 엔티티가 not-null·0~100 보장.
                SEED_TRUST,                       // trust: 대응 컬럼 없음 → 시드(통화 내 이어받기는 2단계)
                SEED_REPAIR_PROGRESS,
                SEED_BREAKUP_RISK,
                daysTogether,
                DEFAULT_STRATEGY,
                relationship.getEmotion(),
                relationship.getSpeechStyle().name(),
                relationship.getSpiceLevel(),
                relationship.getVersion()
        );
    }

    /** 레거시 단계값 → AI 서버 캐논값. ("SOME" 문자열은 서버가 안 받으므로 여기서 변환.) */
    private String mapStage(RelationshipStage stage) {
        return switch (stage) {
            case SOME -> "CRUSH";
            case EARLY_DATING -> "DATING";
            case LONG_TERM -> "DEEP_LOVE";
        };
    }
}
