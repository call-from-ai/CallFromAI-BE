package com.example.umcCall.domain.call.controller;

import com.example.umcCall.domain.call.dto.request.CallDialRequest;
import com.example.umcCall.domain.call.dto.response.CallDetailResponse;
import com.example.umcCall.domain.call.dto.response.CallEndResponse;
import com.example.umcCall.domain.call.dto.response.CallIncomingResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 통화 API. 사용자 발신(dial)과 AI 발신(착신 조회·수락·거절)이 평행하게 있고, 종료·기록 조회가 뒤따른다.
 */
@Tag(name = "통화", description = "통화 발신·착신·종료·기록 조회 API")
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

    @Operation(summary = "착신 대기 통화 조회",
            description = "지금 걸려온 전화(RINGING) 한 건을 반환한다. 착신이 없으면 result 없음. 받기/거절은 이 응답의 callId로 호출한다.")
    @GetMapping("/incoming")
    public ApiResponse<CallIncomingResponse> getIncomingCall(@AuthenticationPrincipal Long memberId) {
        return ApiResponse.onSuccess(callService.getIncomingCall(memberId));
    }

    @Operation(summary = "AI 발신(착신) 수락",
            description = "착신 대기 중(RINGING)인 통화를 받는다. 상태를 PENDING(받았지만 연결 전)으로 전이하고 WebSocket 접속용 단명 wsTicket을 발급한다.")
    @PatchMapping("/{callId}/accept")
    public ApiResponse<CallTicketResponse> accept(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long callId) {
        return ApiResponse.onSuccess(callService.accept(memberId, callId));
    }

    @Operation(summary = "AI 발신(착신) 거절",
            description = "착신 대기 중(RINGING)인 통화를 거절한다. 상태를 REJECTED로 마감하며 wsTicket은 발급하지 않는다.")
    @PatchMapping("/{callId}/reject")
    public ApiResponse<Void> reject(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long callId) {
        callService.reject(memberId, callId);
        return ApiResponse.onSuccess();
    }

    @Operation(summary = "통화 종료(사용자가 끊기)",
            description = "진행 중인 통화를 종료한다. 상태를 COMPLETED로 마감하고 통화 시간을 반환하며, 서버가 WebSocket 세션도 정리한다.")
    @PatchMapping("/{callId}/end")
    public ApiResponse<CallEndResponse> end(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long callId) {
        return ApiResponse.onSuccess(callService.end(memberId, callId));
    }

    @Operation(summary = "내 통화 목록 조회",
            description = "본인의 종료된 통화(완료/취소/부재중/거절)를 최신순 최대 20건 반환한다. 페이지네이션 없음.")
    @GetMapping
    public ApiResponse<CallListResponse> getCallList(@AuthenticationPrincipal Long memberId) {
        return ApiResponse.onSuccess(callHistoryService.getCallList(memberId));
    }

    @Operation(summary = "통화 기록 상세 조회",
            description = """
                    통화 한 건의 상세(요약/시작시각/오디오)를 반환한다. 완료된 본인 소유 통화만 조회할 수 있다.
                    wait=true면 요약·녹음이 준비될 때까지 서버가 상한(서버 설정)까지 기다렸다가 응답한다 —
                    통화 종료 화면에서 폴링 없이 한 번의 조회로 받기 위한 옵션이다.
                    상한을 넘기면 그 시점 상태(summaryStatus/recordingStatus = PROCESSING)로 응답하며,
                    산출물은 백그라운드에서 계속 만들어지므로 그때만 다시 조회하면 된다.""")
    @GetMapping("/{callId}")
    public ApiResponse<CallDetailResponse> getCallDetail(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long callId,
            @RequestParam(defaultValue = "false") boolean wait) {
        return ApiResponse.onSuccess(callHistoryService.getCallDetail(memberId, callId, wait));
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
