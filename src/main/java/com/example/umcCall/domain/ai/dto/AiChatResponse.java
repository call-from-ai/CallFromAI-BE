package com.example.umcCall.domain.ai.dto;

/**
 * AI 서버 {@code POST /chat} 응답. 통화는 대사 필드 {@code reply}만 소비한다.
 * <p>⚠ {@code trust}/{@code repairProgress}/{@code breakupRisk}/{@code strategy}는 원본 필드로 남겨두나,
 * 실제 응답은 이 값들을 중첩({@code relationshipDelta}/{@code nextRelationship})으로 주므로 이 평면 필드는
 * 현재 항상 null이다. 채팅 기능이 delta를 실제 소비할 땐 중첩 구조로 매핑해야 한다.
 */
public record AiChatResponse(
        String reply,
        Double trust,
        Double repairProgress,
        Double breakupRisk,
        String strategy
) {
}
