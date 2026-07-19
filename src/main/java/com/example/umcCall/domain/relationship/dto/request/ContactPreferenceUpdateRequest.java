package com.example.umcCall.domain.relationship.dto.request;

import com.example.umcCall.domain.character.enums.PreferTime;
import jakarta.validation.constraints.NotNull;

public record ContactPreferenceUpdateRequest(
        @NotNull(message = "선호 시간은 필수입니다.") PreferTime preferTime
) {
}
