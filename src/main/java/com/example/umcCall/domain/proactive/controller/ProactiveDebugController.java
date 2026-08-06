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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "테스트 - 선제 연락", description = """
        선제 연락 스케줄러 테스트 API이다. 활성 프로필과 관계없이 등록되므로 배포 서버 Swagger에서도 사용할 수 있다.
        정상 정책 테스트는 상태 조회 → force-due → process → 상태 조회 순서로 진행한다.
        채팅 또는 통화 전달 경로만 빠르게 확인하려면 force-send 또는 force-call을 사용한다.
        force-send와 force-call은 실제 DB, AI 서버, SSE/FCM 또는 착신 푸시에 영향을 준다.
        """)
@RestController
@RequestMapping("/test/proactive")
@RequiredArgsConstructor
public class ProactiveDebugController {

    private final ProactiveDebugService debugService;

    @Operation(
            summary = "현재 스케줄 상태 조회",
            description = """
                    테스트 시작과 실행 후 검증에 사용한다. 회원의 삭제되지 않은 모든 캐릭터 스케줄을 반환하며,
                    스케줄이 없는 관계에는 조회 과정에서 스케줄을 생성한다. nextCheckAt, pendingAction,
                    dailyContactCount, dailyCallCount, lastError를 확인하면 현재 실행 가능 여부와 처리 결과를 파악할 수 있다.
                    """)
    @GetMapping("/{memberId}")
    public ApiResponse<List<ProactiveScheduleResponse>> getStatus(
            @Parameter(description = "테스트할 회원 ID", example = "1")
            @PathVariable Long memberId) {
        return ApiResponse.onSuccess(debugService.getStatuses(memberId));
    }

    @Operation(
            summary = "현재 설정으로 다음 시각 재계산",
            description = """
                    메인 캐릭터의 최신 preferTime, Attachment, 관계 감정을 기준으로 nextCheckAt을 다시 계산한다.
                    설정 변경이 스케줄에 반영되는지 확인할 때 사용한다. AI 서버 호출, 메시지 저장, 통화 생성은 하지 않는다.
                    """)
    @PostMapping("/{memberId}/reschedule")
    public ApiResponse<ProactiveScheduleResponse> reschedule(
            @Parameter(description = "테스트할 회원 ID", example = "1")
            @PathVariable Long memberId) {
        return ApiResponse.onSuccess(debugService.reschedule(memberId));
    }

    @Operation(
            summary = "스케줄 즉시 도래 처리",
            description = """
                    메인 캐릭터의 nextCheckAt을 현재 시각으로 바꿔 도래 상태로 만든다.
                    이 요청만으로 연락이 실행되지는 않는다. 이어서 process를 호출하거나 worker polling을 기다려야 하며,
                    process 시 연락 간격, 미응답, 일일 한도 같은 정상 정책에 의해 다시 연기될 수 있다.
                    """)
    @PostMapping("/{memberId}/force-due")
    public ApiResponse<ProactiveScheduleResponse> forceDue(
            @Parameter(description = "테스트할 회원 ID", example = "1")
            @PathVariable Long memberId) {
        return ApiResponse.onSuccess(debugService.forceDue(memberId));
    }

    @Operation(
            summary = "도래 스케줄 즉시 실행",
            description = """
                    실제 worker와 같은 정책 및 처리 경로를 동기적으로 한 번 실행한다. 먼저 force-due를 호출하는 것을 권장한다.
                    정책 결과가 CHAT이면 AI 서버를 호출해 메시지를 저장하고 SSE/FCM으로 알린다.
                    CALL이면 RINGING 통화를 만들고 착신 푸시를 발행한다. 아직 도래하지 않았거나 정책에 막히면
                    processed=false, result=POLICY_DEFERRED_OR_BLOCKED를 반환하므로 이후 상태 조회에서 nextCheckAt을 확인한다.
                    """)
    @PostMapping("/{memberId}/process")
    public ApiResponse<ProactiveProcessResponse> processNow(
            @Parameter(description = "테스트할 회원 ID", example = "1")
            @PathVariable Long memberId) {
        return ApiResponse.onSuccess(debugService.processNow(memberId));
    }

    @Operation(
            summary = "정책을 우회해 선제 채팅 강제 발송",
            description = """
                    채팅 전달 경로만 빠르게 검증한다. 연락 간격, 선호 시간, 일일 한도 및 행동 선택 정책을 우회해
                    CHAT claim을 만들고 AI 서버를 실제 호출한다. 성공하면 AI 메시지를 DB에 저장하고 SSE 또는 FCM으로 알린다.
                    enabled, 메인 관계, 캐릭터 삭제 여부와 기존 pending 요청은 검증한다.
                    """)
    @PostMapping("/{memberId}/force-send")
    public ApiResponse<ProactiveProcessResponse> forceSend(
            @Parameter(description = "테스트할 회원 ID", example = "1")
            @PathVariable Long memberId) {
        return ApiResponse.onSuccess(debugService.forceSend(memberId));
    }

    @Operation(
            summary = "정책을 우회해 선제 통화 강제 발신",
            description = """
                    통화 착신 경로만 빠르게 검증한다. 연락 간격, 선호 시간, 일일 한도, 관계 상태, 미응답 상태와
                    통화 선택 확률을 우회해 CALL claim을 만든다. 성공하면 AI 발신 RINGING 통화와 착신 푸시를 생성한다.
                    enabled, 메인 관계, 캐릭터 삭제 여부, 기존 pending 요청 및 진행 중인 통화는 검증한다.
                    실패하면 테스트 전 nextCheckAt을 복구한다. 성공하면 실제 완료 경로와 동일하게 dailyContactCount와
                    dailyCallCount를 증가시키고 다음 nextCheckAt을 계산한다. 착신 대기 통화 조회 API에서 생성된 통화를 확인할 수 있다.
                    """)
    @PostMapping("/{memberId}/force-call")
    public ApiResponse<ProactiveProcessResponse> forceCall(
            @Parameter(description = "테스트할 회원 ID", example = "1")
            @PathVariable Long memberId) {
        return ApiResponse.onSuccess(debugService.forceCall(memberId));
    }
}
