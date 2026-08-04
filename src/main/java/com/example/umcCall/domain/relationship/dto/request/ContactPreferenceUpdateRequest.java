package com.example.umcCall.domain.relationship.dto.request;

import com.example.umcCall.domain.character.enums.PreferTime;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record ContactPreferenceUpdateRequest(
        @Schema(example = "LATE_EVENING")
        @NotNull(message = "선호 시간은 필수입니다.")
        PreferTime preferTime
) {
}
