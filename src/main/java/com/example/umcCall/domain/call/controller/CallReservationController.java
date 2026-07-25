package com.example.umcCall.domain.call.controller;

import com.example.umcCall.domain.call.dto.request.CallReservationUpdateRequest;
import com.example.umcCall.domain.call.dto.response.CallReservationListResponse;
import com.example.umcCall.domain.call.service.CallReservationService;
import com.example.umcCall.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 통화 예약 API. 예약 생성은 아직 범위 밖(채팅 대화 또는 AI 서버가 만드는 방식이 미정)이라
 * 조회·수정만 있다.
 */
@Tag(name = "통화 예약", description = "AI 발신(예약 통화) 조회·수정 API")
@RestController
@RequestMapping("/call-reservations")
@RequiredArgsConstructor
public class CallReservationController {

    private final CallReservationService callReservationService;

    @Operation(summary = "내 통화 예약 목록 조회",
            description = "대기 중인 예약을 가까운 시각부터 반환한다. 페이지네이션 없음. 지난 예약의 결과는 통화 기록에서 본다.")
    @GetMapping
    public ApiResponse<CallReservationListResponse> getMyReservations(
            @AuthenticationPrincipal Long memberId) {
        return ApiResponse.onSuccess(callReservationService.getMyReservations(memberId));
    }

    @Operation(summary = "통화 예약 시각 수정",
            description = "대기 중인 예약의 발신 시각을 미래 시각으로 변경한다. 이미 발신·취소된 예약은 수정할 수 없다.")
    @PatchMapping("/{reservationId}")
    public ApiResponse<Void> reschedule(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long reservationId,
            @RequestBody @Valid CallReservationUpdateRequest request) {
        callReservationService.reschedule(memberId, reservationId, request.scheduledAt());
        return ApiResponse.onSuccess();
    }
}
