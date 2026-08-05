package com.example.umcCall.domain.notification.push.dto.request;

import com.example.umcCall.domain.notification.push.enums.PushPlatform;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 푸시 토큰 등록 요청. memberId는 JWT에서 오므로 토큰/플랫폼만 받는다.
 */
public record PushTokenRegisterRequest(
        @Schema(description = "FCM 기기 토큰. 앱 시작·토큰 갱신 시 발급받은 값을 그대로 보냅니다.",
                example = "fcm-device-token-abc123...")
        @NotBlank(message = "FCM 토큰은 필수입니다.")
        String token,

        @Schema(description = "토큰이 발급된 기기 플랫폼", example = "ANDROID")
        @NotNull(message = "플랫폼은 필수입니다. (ANDROID, IOS, WEB)")
        PushPlatform platform
) {
}
