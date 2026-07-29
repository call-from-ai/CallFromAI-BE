package com.example.umcCall.domain.member.dto.request;

public record NotificationSettingUpdateRequest(
        Boolean allNotificationEnabled,
        Boolean nightCallAllowed
) {}