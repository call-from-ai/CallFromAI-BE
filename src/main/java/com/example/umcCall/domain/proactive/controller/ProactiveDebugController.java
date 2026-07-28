package com.example.umcCall.domain.proactive.controller;

import com.example.umcCall.domain.proactive.dto.ProactiveProcessResponse;
import com.example.umcCall.domain.proactive.dto.ProactiveScheduleResponse;
import com.example.umcCall.domain.proactive.service.ProactiveDebugService;
import com.example.umcCall.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "테스트 - 선제 연락", description = "local 프로필 전용 선제 연락 스케줄러 테스트 API")
@RestController
@Profile("local")
@RequestMapping("/test/proactive")
@RequiredArgsConstructor
public class ProactiveDebugController {

    private final ProactiveDebugService debugService;

    @Operation(
            summary = "현재 스케줄 상태 조회",
            description = "회원의 삭제되지 않은 모든 캐릭터 스케줄과 메인 여부, 연락 모드, 선호 시간, Attachment, 미응답 및 재시도 상태를 조회한다."
    )
    @GetMapping("/{memberId}")
    public ApiResponse<List<ProactiveScheduleResponse>> getStatus(
            @Parameter(description = "테스트할 회원 ID", example = "1")
            @PathVariable Long memberId) {
        return ApiResponse.onSuccess(debugService.getStatuses(memberId));
    }

    @Operation(
            summary = "현재 설정으로 다음 시각 재계산",
            description = "캐릭터의 최신 preferTime, Attachment, 관계 감정을 읽어 nextCheckAt을 다시 계산한다. AI는 호출하지 않는다."
    )
    @PostMapping("/{memberId}/reschedule")
    public ApiResponse<ProactiveScheduleResponse> reschedule(
            @Parameter(description = "테스트할 회원 ID", example = "1")
            @PathVariable Long memberId) {
        return ApiResponse.onSuccess(debugService.reschedule(memberId));
    }

    @Operation(
            summary = "스케줄 즉시 도래 처리",
            description = "nextCheckAt을 현재 시각으로 변경한다. AI는 호출하지 않으며 다음 process 요청 또는 scheduler tick에서 정책을 평가한다."
    )
    @PostMapping("/{memberId}/force-due")
    public ApiResponse<ProactiveScheduleResponse> forceDue(
            @Parameter(description = "테스트할 회원 ID", example = "1")
            @PathVariable Long memberId) {
        return ApiResponse.onSuccess(debugService.forceDue(memberId));
    }

    @Operation(
            summary = "도래 스케줄 즉시 실행",
            description = "주의: 하드 필터와 정책을 평가한 뒤 AI 서버를 실제 호출하고 AI 메시지를 DB에 저장한다. 먼저 force-due를 호출하는 것을 권장한다."
    )
    @PostMapping("/{memberId}/process")
    public ApiResponse<ProactiveProcessResponse> processNow(
            @Parameter(description = "테스트할 회원 ID", example = "1")
            @PathVariable Long memberId) {
        return ApiResponse.onSuccess(debugService.processNow(memberId));
    }

    @Operation(
            summary = "정책을 우회해 선제 채팅 강제 발송",
            description = "주의: local 테스트 전용이다. 연락 간격·선호 시간·일일 한도 정책을 우회하고 AI 서버를 실제 호출해 메시지를 DB에 저장한다. enabled·활성 관계·삭제 여부는 검증한다."
    )
    @PostMapping("/{memberId}/force-send")
    public ApiResponse<ProactiveProcessResponse> forceSend(
            @Parameter(description = "테스트할 회원 ID", example = "1")
            @PathVariable Long memberId) {
        return ApiResponse.onSuccess(debugService.forceSend(memberId));
    }
}
