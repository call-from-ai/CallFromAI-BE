package com.example.umcCall.domain.notification.controller;

import com.example.umcCall.domain.notification.dto.response.NotificationResponse;
import com.example.umcCall.domain.notification.service.NotificationService;
import com.example.umcCall.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "알림", description = "홈화면 지난 알림 조회/읽음처리 API")
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "지난 알림 목록 조회", description = "최근 7일간의 알림 목록을 최신순으로 반환한다.")
    @GetMapping
    public ApiResponse<List<NotificationResponse>> getNotifications(@AuthenticationPrincipal Long memberId) {
        return ApiResponse.onSuccess(notificationService.getNotifications(memberId));
    }

    @Operation(summary = "알림 읽음 처리", description = "특정 알림을 읽음 상태로 변경한다.")
    @PatchMapping("/{notificationId}/read")
    public ApiResponse<Void> markAsRead(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long notificationId) {
        notificationService.markAsRead(memberId, notificationId);
        return ApiResponse.onSuccess();
    }

    @Operation(summary = "전체 알림 읽음 처리", description = "안 읽은 알림을 전부 읽음 처리한다. 홈화면 진입 시 호출한다.")
    @PatchMapping("/read-all")
    public ApiResponse<Void> markAllAsRead(@AuthenticationPrincipal Long memberId) {
        notificationService.markAllAsRead(memberId);
        return ApiResponse.onSuccess();
    }
}