package com.example.umcCall.domain.ai.mapper;

import com.example.umcCall.domain.ai.dto.AiCharacterSnapshot;
import com.example.umcCall.domain.character.entity.Character;
import com.example.umcCall.domain.character.entity.CharacterAiProfile;
import com.example.umcCall.domain.relationship.entity.Relationship;
import org.springframework.stereotype.Component;

@Component
public class AiCharacterSnapshotMapper {

    public AiCharacterSnapshot toSnapshot(
            Character character,
            CharacterAiProfile profile,
            Relationship relationship
    ) {
        return new AiCharacterSnapshot(
                character.getId(),
                character.getName(),
                null,
                relationship.getSpeechStyle().name(),
                character.getJob().name(),
                profile.getLifeType(),
                relationship.getSpiceLevel(),
                new AiCharacterSnapshot.TraitProfile(
                        rounded(profile.getHumor()),
                        rounded(profile.getPlayfulness()),
                        rounded(profile.getAffection()),
                        rounded(profile.getEmpathy()),
                        rounded(profile.getAttachment()),
                        rounded(profile.getJealousy()),
                        rounded(profile.getDominance()),
                        rounded(profile.getConfidence()),
                        rounded(profile.getExpressiveness()),
                        rounded(profile.getEmotionalStability()),
                        profile.getCalculationVersion()
                )
        );
    }

    private Integer rounded(Double score) {
        return score == null ? null : (int) Math.round(score);
    }
}
