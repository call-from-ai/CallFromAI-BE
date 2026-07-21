package com.example.umcCall.domain.ai.dto;

/**
 * AI 서버 {@code POST /chat} 응답. 통화는 대사 필드 {@code reply}만 소비하지만,
 * 나머지 필드는 채팅 기능이 관계 delta 반영에 쓰므로 남겨둔다.
 */
public record AiChatResponse(
        String reply,
        Double trust,
        Double repairProgress,
        Double breakupRisk,
        String strategy
) {
}
