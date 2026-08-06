package com.example.umcCall.domain.notification.push.controller;

import com.example.umcCall.domain.notification.push.dto.PushMessage;
import com.example.umcCall.domain.notification.push.dto.request.PushTokenDeleteRequest;
import com.example.umcCall.domain.notification.push.dto.request.PushTokenRegisterRequest;
import com.example.umcCall.domain.notification.push.service.PushNotificationService;
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
    private final PushNotificationService pushNotificationService;

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

    @Operation(summary = "[테스트] FCM 테스트 푸시 발송",
            description = "호출자 본인의 등록된 모든 기기에 'fcm 테스트 알림입니다' 푸시를 보낸다. "
                    + "알림설정(전체알림/방해금지)을 무시하고 발송하며, 발송한(등록된) 기기 수를 반환한다(0이면 토큰 미등록). "
                    + "FCM 토큰 등록 후 기기 수신 확인용 — 정식 기능이 아니다.")
    @PostMapping("/test")
    public ApiResponse<Integer> sendTest(@AuthenticationPrincipal Long memberId) {
        int sentDeviceCount = pushNotificationService.sendTest(memberId,
                PushMessage.notice(0L, "테스트 알림", "fcm 테스트 알림입니다"));
        return ApiResponse.onSuccess(sentDeviceCount);
    }
}
