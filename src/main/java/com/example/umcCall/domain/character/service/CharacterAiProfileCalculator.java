package com.example.umcCall.domain.character.service;

import com.example.umcCall.domain.character.entity.Character;
import com.example.umcCall.domain.character.entity.CharacterTrait;
import java.util.List;

public interface CharacterAiProfileCalculator {

    int calculationVersion();

    CharacterAiProfileScores calculate(Character character, List<CharacterTrait> traits);
}
