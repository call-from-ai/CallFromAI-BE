package com.example.umcCall.domain.member.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

public record DoNotDisturbUpdateRequest(
        @Schema(example = "22:00:00", description = "방해금지 시작 시간")
        @NotNull(message = "시작 시간은 필수입니다.")
        LocalTime startTime,

        @Schema(example = "08:00:00", description = "방해금지 종료 시간")
        @NotNull(message = "종료 시간은 필수입니다.")
        LocalTime endTime
) {}