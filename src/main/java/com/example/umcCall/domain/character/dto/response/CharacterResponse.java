package com.example.umcCall.domain.character.dto.response;

import com.example.umcCall.domain.character.entity.Character;
import com.example.umcCall.domain.character.entity.CharacterTrait;
import com.example.umcCall.domain.character.enums.Gender;
import com.example.umcCall.domain.character.enums.Job;
import com.example.umcCall.domain.character.enums.PreferTime;
import com.example.umcCall.domain.character.enums.SpeechStyle;
import com.example.umcCall.domain.relationship.entity.Relationship;
import com.example.umcCall.domain.relationship.enums.RelationshipStage;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

/**
 * 캐릭터 상세 조회 응답.
 */
@Getter
@Builder
public class CharacterResponse {

    private Long characterId;
    private String name;
    private Gender gender;
    private Integer age;
    private Job job;
    private String imageUrl;
    private Integer spiceLevel;
    private PreferTime preferTime;
    private String mbti;
    private SpeechStyle speechStyle;
    private RelationshipStage relationshipStage;
    private boolean main;
    private List<TraitResponse> traits;

    public static CharacterResponse of(Character character, Relationship relationship,
                                        List<CharacterTrait> characterTraits, String imageUrl) {
        return CharacterResponse.builder()
                .characterId(character.getId())
                .name(character.getName())
                .gender(character.getGender())
                .age(character.getAge())
                .job(character.getJob())
                .imageUrl(imageUrl)
                .spiceLevel(relationship.getSpiceLevel())
                .preferTime(character.getPreferTime())
                .mbti(character.getMbti())
                .speechStyle(relationship.getSpeechStyle())
                .relationshipStage(relationship.getRelationshipStage())
                .main(relationship.isMain())
                .traits(characterTraits.stream().map(TraitResponse::from).toList())
                .build();
    }
}
