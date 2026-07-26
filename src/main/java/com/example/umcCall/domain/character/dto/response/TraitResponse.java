package com.example.umcCall.domain.character.dto.response;

import com.example.umcCall.domain.character.entity.CharacterTrait;
import lombok.Builder;
import lombok.Getter;

/**
 * 캐릭터 응답에 포함되는 매력 키워드 항목.
 */
@Getter
@Builder
public class TraitResponse {

    private String code;
    private String name;
    private Integer priority;

    public static TraitResponse from(CharacterTrait characterTrait) {
        return TraitResponse.builder()
                .code(characterTrait.getTrait().name())
                .name(characterTrait.getTrait().getLabel())
                .priority(characterTrait.getPriority())
                .build();
    }
}
