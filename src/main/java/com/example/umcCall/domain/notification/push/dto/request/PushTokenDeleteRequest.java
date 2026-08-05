package com.example.umcCall.domain.notification.push.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 푸시 토큰 삭제(로그아웃) 요청. 해제할 기기 토큰만 받는다.
 */
public record PushTokenDeleteRequest(
        @Schema(description = "해제할 FCM 기기 토큰(로그아웃하는 기기의 토큰)",
                example = "fcm-device-token-abc123...")
        @NotBlank(message = "FCM 토큰은 필수입니다.")
        String token
) {
}
