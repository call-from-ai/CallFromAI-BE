package com.example.umcCall.domain.auth.controller;

import com.example.umcCall.domain.auth.dto.request.KakaoLoginRequest;
import com.example.umcCall.domain.auth.dto.request.RefreshTokenRequest;
import com.example.umcCall.domain.auth.dto.response.TokenResponse;
import com.example.umcCall.domain.auth.service.AuthService;
import com.example.umcCall.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Tag(name = "인증", description = "카카오 로그인/JWT 재발급/로그아웃 API")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "카카오 로그인", description = """
                    안드로이드에서 전달받은 카카오 액세스 토큰으로 카카오 사용자 정보를 조회한다.
                    로그인 성공 시 JWT Access Token과 Refresh Token을 발급하며,
                    온보딩 필요 여부를 needsOnboarding 값으로 반환한다.
                    """)
    @PostMapping("/kakao")
    public ResponseEntity<ApiResponse<TokenResponse>> kakaoLogin(@Valid @RequestBody KakaoLoginRequest request) {
        TokenResponse tokenResponse = authService.kakaoLogin(request.kakaoAccessToken());
        return ResponseEntity.ok(ApiResponse.onSuccess(tokenResponse));
    }

    @Operation(
            summary = "토큰 재발급",
            description = """
                    유효한 Refresh Token을 사용해 새로운 Access Token과 Refresh Token을 발급한다.
                    요청으로 전달된 Refresh Token이 DB에 저장된 최신 토큰과 일치하는지 검증한다.
                    재발급이 완료되면 기존 Refresh Token은 더 이상 사용할 수 없다.
                    """
    )
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        TokenResponse tokenResponse = authService.reissueToken(request.refreshToken());
        return ResponseEntity.ok(ApiResponse.onSuccess(tokenResponse));
    }

    @Operation(
            summary = "로그아웃",
            description = """
                    인증된 회원의 Refresh Token을 DB에서 삭제 처리한다.
                    카카오 SDK 로그아웃 및 기기에 저장된 토큰 삭제는 안드로이드에서 처리한다.  
                    """
    )
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal Long memberId) {
        authService.logout(memberId);
        return ResponseEntity.noContent().build();
    }
}
