package com.example.umcCall.domain.character.dto.request;

import com.example.umcCall.domain.character.enums.Trait;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 캐릭터 생성 시 전달받는 매력 키워드 항목.
 */
@Getter
@NoArgsConstructor
public class TraitRequest {

    @NotNull
    private Trait trait;

    @NotNull
    @Min(1)
    @Max(5)
    private Integer priority;
}
