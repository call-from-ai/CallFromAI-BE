package com.example.umcCall.domain.ai.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.umcCall.domain.character.entity.Character;
import com.example.umcCall.domain.character.entity.CharacterAiProfile;
import com.example.umcCall.domain.character.entity.CharacterTrait;
import com.example.umcCall.domain.character.enums.Job;
import com.example.umcCall.domain.character.enums.PreferTime;
import com.example.umcCall.domain.character.enums.SpeechStyle;
import com.example.umcCall.domain.character.enums.Trait;
import com.example.umcCall.domain.character.repository.CharacterTraitRepository;
import com.example.umcCall.domain.image.enums.Gender;
import com.example.umcCall.domain.relationship.entity.Relationship;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiCharacterSnapshotMapperTest {

    @Mock
    private CharacterTraitRepository characterTraitRepository;

    @InjectMocks
    private AiCharacterSnapshotMapper mapper;

    @Test
    void includesSelectedKeywordLabelsInPriorityOrderWhileKeepingTraits() {
        Character character = mock(Character.class);
        CharacterAiProfile profile = mock(CharacterAiProfile.class);
        Relationship relationship = mock(Relationship.class);

        when(character.getId()).thenReturn(1L);
        when(character.getFullName()).thenReturn("김다정");
        when(character.getAge()).thenReturn(25);
        when(character.getGender()).thenReturn(Gender.FEMALE);
        when(character.getMbti()).thenReturn("ENFP");
        when(character.getJob()).thenReturn(Job.EMPLOYEE);
        when(character.getPreferTime()).thenReturn(PreferTime.LATE_EVENING);
        when(profile.getLifeType()).thenReturn("BALANCED");
        when(profile.getHumor()).thenReturn(7.0);
        when(profile.getCalculationVersion()).thenReturn(2);
        when(relationship.getSpeechStyle()).thenReturn(SpeechStyle.CASUAL);
        when(relationship.getSpiceLevel()).thenReturn(8);
        when(characterTraitRepository.findByCharacterIdOrderByPriorityAsc(1L))
                .thenReturn(List.of(
                        trait(character, Trait.GOOD_LISTENER, 1),
                        trait(character, Trait.PLAYFUL, 2),
                        trait(character, Trait.EXPRESSIVE, 3)
                ));

        var snapshot = mapper.toSnapshot(character, profile, relationship);

        assertThat(snapshot.keywords())
                .containsExactly("고민을 잘 들어주는", "장난기 많은", "표현을 많이 하는");
        assertThat(snapshot.age()).isEqualTo(25);
        assertThat(snapshot.gender()).isEqualTo("FEMALE");
        assertThat(snapshot.mbti()).isEqualTo("ENFP");
        assertThat(snapshot.traits().humor()).isEqualTo(7);
        assertThat(snapshot.traits().calculationVersion()).isEqualTo(2);
    }

    @Test
    void usesEmptyKeywordsWhenNoKeywordWasSelected() {
        Character character = mock(Character.class);
        CharacterAiProfile profile = mock(CharacterAiProfile.class);
        Relationship relationship = mock(Relationship.class);

        when(character.getId()).thenReturn(1L);
        when(character.getJob()).thenReturn(Job.EMPLOYEE);
        when(character.getPreferTime()).thenReturn(PreferTime.LATE_EVENING);
        when(relationship.getSpeechStyle()).thenReturn(SpeechStyle.CASUAL);
        when(characterTraitRepository.findByCharacterIdOrderByPriorityAsc(1L))
                .thenReturn(List.of());

        assertThat(mapper.toSnapshot(character, profile, relationship).keywords()).isEmpty();
    }

    private CharacterTrait trait(Character character, Trait trait, int priority) {
        return CharacterTrait.builder()
                .character(character)
                .trait(trait)
                .priority(priority)
                .build();
    }
}
