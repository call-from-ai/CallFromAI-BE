package com.example.umcCall.domain.ai.dto;

/**
 * AI 서버 계약의 관계 스냅샷.
 * <p>계약 점수(temperature/trust/repair/breakup)는 서버가 필수 검증하고, stage는 캐논 enum이라 값 변환이 필요하다.
 * emotion/speechStyle/spiceLevel은 원본 필드로 남겨둔다(서버는 무시). version은 우리 stale 가드에서 쓴다.
 *
 * @param relationshipStage            캐논값 "CRUSH" | "DATING" | "DEEP_LOVE"
 * @param relationshipTemperatureScore 관계 온도(0–100, 서버 pass-through)
 * @param strategy                     "NORMAL" | "CONFLICT_REPAIR"
 */
public record AiRelationshipSnapshot(
        Long relationshipId,
        String relationshipStage,
        int relationshipTemperatureScore,
        int trust,
        int repairProgress,
        int breakupRisk,
        long daysTogether,
        String strategy,
        String emotion,
        String speechStyle,
        Integer spiceLevel,
        Long version
) {
}
