package com.example.umcCall.domain.character.service;

import com.example.umcCall.domain.character.entity.Character;
import com.example.umcCall.domain.character.entity.CharacterAiProfile;
import com.example.umcCall.domain.character.entity.CharacterTrait;
import com.example.umcCall.domain.character.repository.CharacterAiProfileRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CharacterAiProfileServiceImpl implements CharacterAiProfileService {

    private final CharacterAiProfileRepository characterAiProfileRepository;
    private final CharacterAiProfileCalculator calculator;

    @Override
    public void calculateAndSave(Character character, List<CharacterTrait> traits) {
        CharacterAiProfile profile = characterAiProfileRepository.findById(character.getId())
                .orElseGet(() -> CharacterAiProfile.create(character));

        profile.update(calculator.calculate(traits), calculator.calculationVersion());
        characterAiProfileRepository.save(profile);
    }
}
