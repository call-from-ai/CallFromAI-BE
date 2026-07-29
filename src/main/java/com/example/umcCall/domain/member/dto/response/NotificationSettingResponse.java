package com.example.umcCall.domain.member.dto.response;

import com.example.umcCall.domain.member.entity.Member;

import java.time.LocalTime;

public record NotificationSettingResponse(
        boolean allNotificationEnabled,
        boolean nightCallAllowed,
        LocalTime doNotDisturbStart,
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