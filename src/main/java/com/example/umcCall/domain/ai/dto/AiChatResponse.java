package com.example.umcCall.domain.ai.dto;

public record AiChatResponse(
        String message,
        Double trust,
        Double repairProgress,
        Double breakupRisk,
        String strategy
) {
}
