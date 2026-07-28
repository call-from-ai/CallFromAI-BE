package com.example.umcCall.domain.proactive.dto;

public record ProactiveProcessResponse(
        boolean processed,
        boolean messageSaved,
        String requestId,
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
