package com.example.umcCall.domain.notification.push.controller;

import com.example.umcCall.domain.notification.push.dto.request.PushTokenDeleteRequest;
import com.example.umcCall.domain.notification.push.dto.request.PushTokenRegisterRequest;
import com.example.umcCall.domain.notification.push.service.PushTokenService;
import com.example.umcCall.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "푸시 토큰", description = "FCM 디바이스 토큰 등록/삭제 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/push-tokens")
public class PushTokenController {

    private final PushTokenService pushTokenService;

    @Operation(summary = "푸시 토큰 등록",
            description = "FCM 기기 토큰을 등록한다. 같은 토큰을 다시 보내면 갱신되며(멱등), 앱 시작/토큰 갱신 시 호출한다.")
    @PostMapping
    public ApiResponse<Void> register(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody PushTokenRegisterRequest request) {
        pushTokenService.register(memberId, request);
        return ApiResponse.onSuccess();
    }

    @Operation(summary = "푸시 토큰 삭제",
            description = "로그아웃 시 해당 기기 토큰을 해제한다. 본인 소유 토큰만 지우며, 없는 토큰이어도 성공한다(멱등).")
    @DeleteMapping
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody PushTokenDeleteRequest request) {
        pushTokenService.delete(memberId, request.token());
        return ApiResponse.onSuccess();
    }
}
