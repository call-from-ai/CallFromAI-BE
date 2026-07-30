package com.example.umcCall.domain.notification.push.dto.request;

import com.example.umcCall.domain.notification.push.enums.PushPlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 푸시 토큰 등록 요청. memberId는 JWT에서 오므로 토큰/플랫폼만 받는다.
 */
public record PushTokenRegisterRequest(
        @NotBlank(message = "FCM 토큰은 필수입니다.")
        String token,

        @NotNull(message = "플랫폼은 필수입니다. (ANDROID, IOS, WEB)")
        PushPlatform platform
) {
}
