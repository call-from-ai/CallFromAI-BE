package com.example.umcCall.domain.character.service;

import com.example.umcCall.domain.character.entity.Character;
import com.example.umcCall.domain.character.entity.CharacterTrait;
import java.util.List;

public interface CharacterAiProfileService {

    void calculateAndSave(Character character, List<CharacterTrait> traits);

    void recalculateOutdatedProfiles();
}
