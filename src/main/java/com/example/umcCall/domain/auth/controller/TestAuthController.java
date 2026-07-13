package com.example.umcCall.domain.auth.controller;

import com.example.umcCall.domain.auth.dto.response.TokenResponse;
import com.example.umcCall.domain.auth.service.AuthService;
import com.example.umcCall.global.apiPayload.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("local")
@RequiredArgsConstructor
public class TestAuthController {

    private final AuthService authService;

    @PostMapping("/test/auth/login")
    public ResponseEntity<ApiResponse<TokenResponse>> testLogin(
            @RequestParam(defaultValue = "test-user-1") String socialUid
    ) {
        TokenResponse tokenResponse = authService.testLogin(socialUid);
        return ResponseEntity.ok(ApiResponse.onSuccess(tokenResponse));
    }
}