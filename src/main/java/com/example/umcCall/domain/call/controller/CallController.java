package com.example.umcCall.domain.call.controller;

import com.example.umcCall.domain.call.dto.request.CallDialRequest;
import com.example.umcCall.domain.call.dto.response.CallDetailResponse;
import com.example.umcCall.domain.call.dto.response.CallListResponse;
import com.example.umcCall.domain.call.dto.response.CallScriptResponse;
import com.example.umcCall.domain.call.dto.response.CallTicketResponse;
import com.example.umcCall.domain.call.service.CallHistoryService;
import com.example.umcCall.domain.call.service.CallService;
import com.example.umcCall.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 통화 API. 현재 범위는 사용자 발신(dial)뿐 — AI 발신(착신)은 후순위로 여기에 평행하게 붙는다.
 */
@Tag(name = "통화", description = "통화 발신 API")
@RestController
@RequestMapping("/calls")
@RequiredArgsConstructor
public class CallController {

    private final CallService callService;
    private final CallHistoryService callHistoryService;

    @Operation(summary = "사용자 발신(dial)",
            description = "메인(활성) 캐릭터에게 통화를 건다. Call을 생성하고 WebSocket 접속용 단명 wsTicket을 발급한다.")
    @PostMapping
    public ApiResponse<CallTicketResponse> dial(
            @AuthenticationPrincipal Long memberId,
            @RequestBody @Valid CallDialRequest request) {
        return ApiResponse.onSuccess(callService.dial(memberId, request.characterId()));
    }

    @Operation(summary = "AI 발신(착신) 수락",
            description = "착신 대기 중(RINGING)인 통화를 받는다. 상태를 PENDING(받았지만 연결 전)으로 전이하고 WebSocket 접속용 단명 wsTicket을 발급한다.")
    @PatchMapping("/{callId}/accept")
    public ApiResponse<CallTicketResponse> accept(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long callId) {
        return ApiResponse.onSuccess(callService.accept(memberId, callId));
    }

    @Operation(summary = "내 통화 목록 조회",
            description = "본인의 종료된 통화(완료/취소/부재중/거절)를 최신순 최대 20건 반환한다. 페이지네이션 없음.")
    @GetMapping
    public ApiResponse<CallListResponse> getCallList(@AuthenticationPrincipal Long memberId) {
        return ApiResponse.onSuccess(callHistoryService.getCallList(memberId));
    }

    @Operation(summary = "통화 기록 상세 조회",
            description = "통화 한 건의 상세(요약/시작시각/오디오)를 반환한다. 완료된 본인 소유 통화만 조회할 수 있다.")
    @GetMapping("/{callId}")
    public ApiResponse<CallDetailResponse> getCallDetail(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long callId) {
        return ApiResponse.onSuccess(callHistoryService.getCallDetail(memberId, callId));
    }

    @Operation(summary = "통화 전사(script) 조회",
            description = "통화 전문을 발화 순서(과거→최신)대로 전체 반환한다. 본인 소유 통화만 조회할 수 있다.")
    @GetMapping("/{callId}/script")
    public ApiResponse<CallScriptResponse> getScript(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long callId) {
        return ApiResponse.onSuccess(callHistoryService.getScript(memberId, callId));
    }
}
