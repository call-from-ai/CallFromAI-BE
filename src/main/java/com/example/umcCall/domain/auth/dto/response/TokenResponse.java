package com.example.umcCall.domain.auth.dto.response;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        boolean needsOnboarding
) {}