package com.example.umcCall.domain.auth.controller;

import com.example.umcCall.domain.auth.dto.KakaoLoginRequest;
import com.example.umcCall.domain.auth.dto.RefreshTokenRequest;
import com.example.umcCall.domain.auth.dto.TokenResponse;
import com.example.umcCall.domain.auth.service.AuthService;
import com.example.umcCall.global.apiPayload.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/kakao")
    public ResponseEntity<ApiResponse<TokenResponse>> kakaoLogin(@Valid @RequestBody KakaoLoginRequest request) {
        TokenResponse tokenResponse = authService.kakaoLogin(request.kakaoAccessToken());
        return ResponseEntity.ok(ApiResponse.onSuccess(tokenResponse));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        TokenResponse tokenResponse = authService.reissueToken(request.refreshToken());
        return ResponseEntity.ok(ApiResponse.onSuccess(tokenResponse));
    }
}