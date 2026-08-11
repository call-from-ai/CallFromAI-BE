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
    @NotNull(message = "우선순위는 필수입니다.")
    @Min(value = 1, message = "우선순위는 1 이상이어야 합니다.")
    @Max(value = 5, message = "우선순위는 5 이하여야 합니다.")
    private Integer priority;
}
