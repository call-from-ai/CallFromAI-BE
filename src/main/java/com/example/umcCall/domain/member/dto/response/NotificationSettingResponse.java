package com.example.umcCall.domain.member.dto.response;

import com.example.umcCall.domain.member.entity.Member;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalTime;

public record NotificationSettingResponse(
        @Schema(example = "true", description = "전체 알림 활성화 여부")
        boolean allNotificationEnabled,
        @Schema(example = "true", description = "심야 통화 허용 여부")
        boolean nightCallAllowed,
        @Schema(
                implementation = String.class,
                example = "22:00:00",
                description = "방해금지 시작 시간, HH:mm:ss 형식",
                nullable = true
        )
        @JsonFormat(pattern = "HH:mm:ss")
        LocalTime doNotDisturbStart,
        @Schema(
                implementation = String.class,
                example = "08:00:00",
                description = "방해금지 종료 시간, HH:mm:ss 형식",
                nullable = true
        )
        @JsonFormat(pattern = "HH:mm:ss")
        LocalTime doNotDisturbEnd
) {
    public static NotificationSettingResponse from(Member member) {
        return new NotificationSettingResponse(
                member.isAllNotificationEnabled(),
                member.isNightCallAllowed(),
                member.getDoNotDisturbStart(),
                member.getDoNotDisturbEnd()
        );
    }
}