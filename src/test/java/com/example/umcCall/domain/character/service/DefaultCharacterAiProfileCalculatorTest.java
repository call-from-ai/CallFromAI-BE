package com.example.umcCall.domain.character.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.umcCall.domain.character.entity.Character;
import com.example.umcCall.domain.character.entity.CharacterTrait;
import com.example.umcCall.domain.character.enums.Job;
import com.example.umcCall.domain.character.enums.Trait;
import java.util.List;
import org.junit.jupiter.api.Test;

class DefaultCharacterAiProfileCalculatorTest {

    @Test
    void calculatesTraitsLifeTypeAndMbtiAdjustments() {
        Character character = Character.builder().job(Job.EMPLOYEE).mbti("ENFP").build();
        List<CharacterTrait> traits = List.of(
                trait(character, Trait.HUMOROUS, 1),
                trait(character, Trait.AFFECTIONATE, 2),
                trait(character, Trait.GOOD_LISTENER, 3));

        var result = CharacterAiProfileService.calculate(character, traits);

        assertThat(result.lifeType()).isEqualTo("WORKER");
        assertThat(result.humor()).isEqualTo(9);
        assertThat(result.playfulness()).isEqualTo(6.5);
        assertThat(result.affection()).isEqualTo(8);
        assertThat(result.empathy()).isEqualTo(9);
        assertThat(result.attachment()).isEqualTo(3);
        assertThat(result.jealousy()).isEqualTo(3);
        assertThat(result.dominance()).isEqualTo(3.5);
        assertThat(result.confidence()).isEqualTo(3);
        assertThat(result.expressiveness()).isEqualTo(8.5);
        assertThat(result.emotionalStability()).isEqualTo(5);
    }

    @Test
    void clampsScoresToPolicyRange() {
        Character character = Character.builder().job(Job.UNEMPLOYED).mbti(null).build();
        List<CharacterTrait> traits = List.of(
                trait(character, Trait.JEALOUS, 1),
                trait(character, Trait.POSSESSIVE, 2),
                trait(character, Trait.EXCLUSIVE, 3));

        var result = CharacterAiProfileService.calculate(character, traits);

        assertThat(result.lifeType()).isEqualTo("FLEXIBLE");
        assertThat(result.attachment()).isEqualTo(10);
        assertThat(result.emotionalStability()).isZero();
    }

    private CharacterTrait trait(Character character, Trait trait, int priority) {
        return CharacterTrait.builder().character(character).trait(trait).priority(priority).build();
    }
}
