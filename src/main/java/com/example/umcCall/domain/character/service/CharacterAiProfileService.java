package com.example.umcCall.domain.character.service;

import com.example.umcCall.domain.character.entity.Character;
import com.example.umcCall.domain.character.entity.CharacterAiProfile;
import com.example.umcCall.domain.character.entity.CharacterTrait;
import com.example.umcCall.domain.character.repository.CharacterAiProfileRepository;
import com.example.umcCall.domain.character.repository.CharacterTraitRepository;
import com.example.umcCall.domain.ai.enums.CharacterSyncOperation;
import com.example.umcCall.domain.ai.service.CharacterSyncTaskService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CharacterAiProfileService implements ApplicationRunner {

    private static final int CALCULATION_VERSION = 2;
    private static final double MIN_SCORE = 0;
    private static final double MAX_SCORE = 10;

    private final CharacterAiProfileRepository characterAiProfileRepository;
    private final CharacterTraitRepository characterTraitRepository;
    private final CharacterSyncTaskService syncTaskService;

    public void calculateAndSave(Character character, List<CharacterTrait> traits) {
        CharacterAiProfile profile = characterAiProfileRepository.findById(character.getId())
                .orElseGet(() -> CharacterAiProfile.create(character));

        profile.update(calculate(character, traits), CALCULATION_VERSION);
        characterAiProfileRepository.save(profile);
        syncTaskService.enqueue(character.getId(), CharacterSyncOperation.UPSERT);
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        characterAiProfileRepository.findAll().stream()
                .filter(profile -> profile.getCalculationVersion() == null
                        || profile.getCalculationVersion() < CALCULATION_VERSION)
                .forEach(profile -> calculateAndSave(
                        profile.getCharacter(),
                        characterTraitRepository.findByCharacterId(profile.getCharacterId())));
    }

    static CharacterAiProfile.Scores calculate(Character character, List<CharacterTrait> traits) {
        double[] scores = {3, 3, 3, 3, 3, 3, 3, 3, 3, 3};
        for (CharacterTrait selected : traits) applyTrait(scores, selected);
        applyMbti(scores, character.getMbti());
        for (int i = 0; i < scores.length; i++) {
            scores[i] = Math.max(MIN_SCORE, Math.min(MAX_SCORE, scores[i]));
        }
        return new CharacterAiProfile.Scores(lifeType(character), scores[0], scores[1], scores[2], scores[3],
                scores[4], scores[5], scores[6], scores[7], scores[8], scores[9]);
    }

    private static void applyTrait(double[] s, CharacterTrait selected) {
        switch (selected.getTrait()) {
            case HUMOROUS -> add(s, 5, 2, 0, 0, 0, 0, 0, 0, 0, 0);
            case PLAYFUL -> add(s, 2, 5, 0, 0, 0, 0, 0, 1, 0, 0);
            case AFFECTIONATE -> add(s, 0, 0, 5, 0, 0, 0, 0, 0, 4, 0);
            case JEALOUS -> add(s, 0, 0, 0, 0, 3, 5, 0, 0, 0, -2);
            case TALKATIVE -> add(s, 0, 0, 1, 0, 0, 0, 0, 0, 5, 0);
            case DAD_JOKE_LOVER -> add(s, 4, 0, 0, 0, 0, 0, 0, 2, 0, 0);
            case HOMEBODY -> add(s, 0, 0, 1, 0, 0, 0, 0, 0, 0, 2);
            case TEASING -> add(s, 0, 4, 0, 0, 0, 0, 2, 2, 0, 0);
            case POSSESSIVE -> add(s, 0, 0, 0, 0, 5, 2, 0, 0, 0, -3);
            case TSUNDERE -> add(s, 0, 0, 2, 0, 0, 0, 0, 2, -2, 0);
            case EXPRESSIVE -> add(s, 0, 0, 3, 0, 0, 0, 0, 0, 5, 0);
            case PET_NAME_LOVER -> add(s, 0, 0, 4, 0, 0, 0, 0, 0, 3, 0);
            case EXCLUSIVE -> add(s, 0, 0, 0, 0, 4, 4, 1, 0, 0, 0);
            case QUIRKY -> add(s, 3, 3, 0, 0, 0, 0, 0, 0, 0, 0);
            case LAID_BACK -> add(s, 0, 0, 0, 0, -1, -2, 0, 0, 0, 5);
            case OPENLY_JEALOUS -> add(s, 0, 0, 0, 0, 0, 4, 0, 0, 4, 0);
            case SHY -> add(s, 0, 0, 2, 0, 0, 0, 0, -4, -2, 0);
            case SMOOTH_TALKER -> add(s, 2, 2, 0, 0, 0, 0, 0, 5, 0, 0);
            case FREQUENT_CHECKER -> add(s, 0, 0, 2, 0, 4, 0, 0, 0, 0, 0);
            case GOOD_LISTENER -> add(s, 0, 0, 0, 5, 0, 0, 0, 0, 0, 2);
            case COMPLIMENTER -> add(s, 0, 0, 3, 2, 0, 0, 0, 0, 3, 0);
        }
    }

    private static void applyMbti(double[] s, String mbti) {
        if (mbti == null || !mbti.toUpperCase().matches("[EI][NS][TF][JP]")) return;
        String value = mbti.toUpperCase();
        if (value.charAt(0) == 'E') add(s, 0, .5, 0, 0, 0, 0, .5, 0, 1, 0);
        else add(s, 0, 0, 0, .5, .5, 0, 0, 0, -1, 0);
        if (value.charAt(1) == 'N') add(s, .5, .5, 0, 0, 0, 0, 0, 0, 0, 0);
        else add(s, 0, 0, 0, .5, 0, 0, 0, 0, 0, .5);
        if (value.charAt(2) == 'T') add(s, 0, 0, 0, -.5, 0, 0, 0, 0, 0, .5);
        else add(s, 0, 0, 0, 1, 0, 0, 0, 0, .5, 0);
        if (value.charAt(3) == 'J') add(s, 0, 0, 0, 0, 0, 0, .5, 0, 0, .5);
        else add(s, .5, .5, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    private static String lifeType(Character character) {
        return switch (character.getJob()) {
            case STUDENT -> "STUDENT";
            case EMPLOYED -> "WORKER";
            case UNEMPLOYED -> "FLEXIBLE";
        };
    }

    private static void add(double[] target, double... delta) {
        for (int i = 0; i < target.length; i++) target[i] += delta[i];
    }
}
