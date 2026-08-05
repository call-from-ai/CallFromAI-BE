package com.example.umcCall.domain.proactive.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "선제 연락 즉시 실행 결과. processed가 false이면 result에서 실행되지 않은 이유를 확인한다.")
public record ProactiveProcessResponse(
        @Schema(description = "채팅 메시지 저장 또는 착신 통화 생성까지 처리되었는지 여부", example = "true")
        boolean processed,
        @Schema(description = "AI 선제 채팅 메시지가 DB에 저장되었는지 여부. 통화 성공 시에는 false이다.", example = "false")
        boolean messageSaved,
        @Schema(description = "멱등성 및 pending 상태 추적용 요청 ID. 실행이 생략되면 null이다.", example = "proactive-debug-call-550e8400-e29b-41d4-a716-446655440000")
        String requestId,
        @Schema(
                description = "처리 결과 코드. AI_MESSAGE_SAVED, AI_CALL_RINGING, POLICY_DEFERRED_OR_BLOCKED, CALL_NOT_CREATED, CLAIM_CANCELED, DISABLED_OR_INACTIVE, DISABLED_INACTIVE_OR_PENDING 중 하나",
                example = "AI_CALL_RINGING")
        String result
) {
    public static ProactiveProcessResponse skipped(String result) {
        return new ProactiveProcessResponse(false, false, null, result);
    }

    public static ProactiveProcessResponse completed(String requestId) {
        return new ProactiveProcessResponse(true, true, requestId, "AI_MESSAGE_SAVED");
    }

    public static ProactiveProcessResponse callRinging(String requestId) {
        return new ProactiveProcessResponse(true, false, requestId, "AI_CALL_RINGING");
    }
}
