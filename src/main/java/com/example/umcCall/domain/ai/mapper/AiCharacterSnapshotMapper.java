package com.example.umcCall.domain.ai.mapper;

import com.example.umcCall.domain.ai.dto.AiCharacterSnapshot;
import com.example.umcCall.domain.character.entity.Character;
import com.example.umcCall.domain.character.entity.CharacterTrait;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AiCharacterSnapshotMapper {

    public AiCharacterSnapshot toSnapshot(Character character, List<CharacterTrait> traits) {
        List<AiCharacterSnapshot.TraitItem> traitItems = traits.stream()
                .map(trait -> new AiCharacterSnapshot.TraitItem(
                        trait.getTrait().name(), trait.getPriority()))
                .toList();

        return new AiCharacterSnapshot(
                character.getId(),
                character.getName(),
                character.getGender().name(),
                character.getAge(),
                character.getJob().name(),
                character.getPreferTime().name(),
                character.getMbti(),
                traitItems
        );
    }
}
