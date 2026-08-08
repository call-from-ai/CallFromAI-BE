package com.example.umcCall.domain.ai.mapper;

import com.example.umcCall.domain.ai.dto.AiCharacterSnapshot;
import com.example.umcCall.domain.character.entity.Character;
import com.example.umcCall.domain.character.entity.CharacterAiProfile;
import com.example.umcCall.domain.character.entity.CharacterTrait;
import com.example.umcCall.domain.character.repository.CharacterTraitRepository;
import com.example.umcCall.domain.relationship.entity.Relationship;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiCharacterSnapshotMapper {

    private final CharacterTraitRepository characterTraitRepository;

    public AiCharacterSnapshot toSnapshot(
            Character character,
            CharacterAiProfile profile,
            Relationship relationship
    ) {
        List<String> keywords = characterTraitRepository
                .findByCharacterIdOrderByPriorityAsc(character.getId())
                .stream()
                .map(CharacterTrait::getTrait)
                .map(trait -> trait.getLabel())
                .toList();

        return new AiCharacterSnapshot(
                character.getId(),
                character.getFullName(),
                relationship.getSpeechStyle().name(),
                character.getJob().name(),
                profile.getLifeType(),
                character.getPreferTime().name(),
                relationship.getSpiceLevel(),
                keywords,
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
