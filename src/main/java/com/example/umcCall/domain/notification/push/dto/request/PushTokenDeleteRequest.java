package com.example.umcCall.domain.notification.push.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 푸시 토큰 삭제(로그아웃) 요청. 해제할 기기 토큰만 받는다.
 */
public record PushTokenDeleteRequest(
        @NotBlank(message = "FCM 토큰은 필수입니다.")
        String token
) {
}
