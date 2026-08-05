package com.example.umcCall.domain.character.dto.request;

import com.example.umcCall.domain.character.enums.Trait;
import io.swagger.v3.oas.annotations.media.Schema;
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

    @Schema(
            example = "HUMOROUS",
            description = "캐릭터 매력 키워드"
    )
    @NotNull
    private Trait trait;

    @Schema(
            example = "1",
            description = "우선순위, 1부터 연속된 값"
    )
    @NotNull
    @Min(1)
    @Max(5)
    private Integer priority;
}
