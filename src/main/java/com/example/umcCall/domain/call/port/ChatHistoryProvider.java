package com.example.umcCall.domain.call.port;

import com.example.umcCall.domain.ai.dto.AiChatHistoryItem;
import java.util.List;

/**
 * 통화가 소유하는 아웃바운드 포트. 통화 시작 시 LLM 맥락을 "빈 상태"가 아니라 그 관계의 채팅 최근 대화로
 * 이어받기 위해 쓴다. call은 이 인터페이스에만 의존하고, 채팅 저장 구조는 어댑터가 숨긴다.
 */
public interface ChatHistoryProvider {

    /**
     * 관계의 채팅 최근 대화를 과거→최신 순으로 최대 {@code limit}개 돌려준다(AI 히스토리 계약 형태).
     * 채팅방이 없으면 빈 리스트.
     *
     * @param relationshipId 관계 ID(통화 티켓에서 온다)
     * @param limit          최대 개수
     */
    List<AiChatHistoryItem> recentHistory(Long relationshipId, int limit);
}
