package com.example.umcCall.domain.character.dto.response;

import com.example.umcCall.domain.character.enums.Trait;
import lombok.Builder;
import lombok.Getter;

/**
 * 매력 키워드 선택지 조회 응답.
 */
@Getter
@Builder
public class TraitOptionResponse {

    private String name;

    public static TraitOptionResponse from(Trait trait) {
        return TraitOptionResponse.builder()
                .name(trait.getLabel())
                .build();
    }
}