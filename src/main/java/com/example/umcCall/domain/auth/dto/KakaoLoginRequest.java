package com.example.umcCall.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record KakaoLoginRequest(
        @NotBlank(message = "카카오 액세스 토큰은 필수입니다.")
        String kakaoAccessToken
) {}