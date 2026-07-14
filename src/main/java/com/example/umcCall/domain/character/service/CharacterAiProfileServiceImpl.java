package com.example.umcCall.domain.character.service;

import com.example.umcCall.domain.character.entity.Character;
import com.example.umcCall.domain.character.entity.CharacterAiProfile;
import com.example.umcCall.domain.character.entity.CharacterTrait;
import com.example.umcCall.domain.character.repository.CharacterAiProfileRepository;
import com.example.umcCall.domain.ai.event.CharacterAiSyncEvent;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;

@Service
@RequiredArgsConstructor
public class CharacterAiProfileServiceImpl implements CharacterAiProfileService {

    private final CharacterAiProfileRepository characterAiProfileRepository;
    private final CharacterAiProfileCalculator calculator;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void calculateAndSave(Character character, List<CharacterTrait> traits) {
        CharacterAiProfile profile = characterAiProfileRepository.findById(character.getId())
                .orElseGet(() -> CharacterAiProfile.create(character));

        profile.update(calculator.calculate(traits), calculator.calculationVersion());
        characterAiProfileRepository.save(profile);
        eventPublisher.publishEvent(new CharacterAiSyncEvent.Upsert(character.getId()));
    }
}
