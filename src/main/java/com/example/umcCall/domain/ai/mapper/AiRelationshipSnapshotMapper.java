package com.example.umcCall.domain.ai.mapper;

import com.example.umcCall.domain.ai.dto.AiRelationshipSnapshot;
import com.example.umcCall.domain.relationship.entity.Relationship;
import com.example.umcCall.domain.relationship.entity.RelationshipStatus;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;

@Component
public class AiRelationshipSnapshotMapper {

    public AiRelationshipSnapshot toSnapshot(
            Relationship relationship,
            RelationshipStatus relationshipStatus
    ) {
        LocalDate startedAt = relationshipStatus.getCreatedAt().toLocalDate();
        long daysTogether = Math.max(0, ChronoUnit.DAYS.between(startedAt, LocalDate.now()));

        return new AiRelationshipSnapshot(
                relationship.getId(),
                relationship.getRelationshipStage().name(),
                relationship.getAffinityScore(),
                null,
                null,
                relationship.getSpeechStyle().name(),
                relationship.getSpiceLevel(),
                daysTogether,
                null
        );
    }
}
