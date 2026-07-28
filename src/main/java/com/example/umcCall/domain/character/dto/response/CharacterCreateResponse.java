package com.example.umcCall.domain.character.dto.response;

import com.example.umcCall.domain.character.entity.Character;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CharacterCreateResponse {

    private Long characterId;
    private String name;

    public static CharacterCreateResponse from(Character character) {
        return CharacterCreateResponse.builder()
                .characterId(character.getId())
                .name(character.getLastName())
                .build();
    }
}