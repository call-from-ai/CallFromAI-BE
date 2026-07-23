package com.example.umcCall.domain.call.controller;

import com.example.umcCall.domain.call.dto.request.CallDialRequest;
import com.example.umcCall.domain.call.dto.response.CallDialResponse;
import com.example.umcCall.domain.call.service.CallService;
import com.example.umcCall.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

    @Operation(summary = "사용자 발신(dial)",
            description = "메인(활성) 캐릭터에게 통화를 건다. Call을 생성하고 WebSocket 접속용 단명 wsTicket을 발급한다.")
    @PostMapping
    public ApiResponse<CallDialResponse> dial(
            @AuthenticationPrincipal Long memberId,
            @RequestBody @Valid CallDialRequest request) {
        return ApiResponse.onSuccess(callService.dial(memberId, request.characterId()));
    }
}
