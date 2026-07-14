package com.example.umcCall.domain.ai.mapper;

import com.example.umcCall.domain.ai.dto.AiCharacterSnapshot;
import com.example.umcCall.domain.character.entity.Character;
import com.example.umcCall.domain.character.entity.CharacterAiProfile;
import org.springframework.stereotype.Component;

@Component
public class AiCharacterSnapshotMapper {

    public AiCharacterSnapshot toSnapshot(
            Character character,
            CharacterAiProfile profile
    ) {
        return new AiCharacterSnapshot(
                character.getId(),
                character.getName(),
                character.getJob().name(),
                profile.getLifeType(),
                new AiCharacterSnapshot.TraitProfile(
                        profile.getHumor(),
                        profile.getPlayfulness(),
                        profile.getAffection(),
                        profile.getEmpathy(),
                        profile.getAttachment(),
                        profile.getJealousy(),
                        profile.getDominance(),
                        profile.getConfidence(),
                        profile.getExpressiveness(),
                        profile.getEmotionalStability(),
                        profile.getCalculationVersion()
                )
        );
    }
}
