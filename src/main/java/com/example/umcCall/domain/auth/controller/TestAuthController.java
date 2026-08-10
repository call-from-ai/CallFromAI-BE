package com.example.umcCall.domain.auth.controller;

import com.example.umcCall.domain.auth.dto.response.TokenResponse;
import com.example.umcCall.domain.auth.service.AuthService;
import com.example.umcCall.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "테스트 로그인", description = "로컬 개발 및 Swagger 테스트를 위한 임시 인증 API")
@RestController
@RequiredArgsConstructor
public class TestAuthController {

    private final AuthService authService;

    @Operation(
            summary = "테스트 로그인",
            description = """
                    로컬 개발 환경에서 카카오 로그인 없이 테스트용 회원을 생성하거나 조회하고
                    서비스 Access Token과 Refresh Token을 발급한다.
                    Swagger 테스트 시 응답으로 받은 accessToken을 우측 상단 Authorize에 입력하여 사용한다.
                    운영 서버에는 보안을 위해 test.access.secret이 설정되어 있어 X-Test-Secret 헤더에 그 값을 담아야 한다.
                    """
    )
    @PostMapping("/test/auth/login")
    public ResponseEntity<ApiResponse<TokenResponse>> testLogin(
            @RequestParam(defaultValue = "1") String socialUid
    ) {
        TokenResponse tokenResponse = authService.testLogin(socialUid);
        return ResponseEntity.ok(ApiResponse.onSuccess(tokenResponse));
    }
}