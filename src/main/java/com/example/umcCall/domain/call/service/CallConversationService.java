package com.example.umcCall.domain.call.service;

import com.example.umcCall.domain.ai.dto.AiChatHistoryItem;
import com.example.umcCall.domain.ai.dto.AiChatRequest;
import com.example.umcCall.domain.ai.dto.AiChatResponse;
import com.example.umcCall.domain.ai.mapper.AiCharacterSnapshotMapper;
import com.example.umcCall.domain.ai.mapper.AiRelationshipSnapshotMapper;
import com.example.umcCall.domain.ai.service.AiConversationService;
import com.example.umcCall.domain.character.entity.Character;
import com.example.umcCall.domain.character.entity.CharacterAiProfile;
import com.example.umcCall.domain.character.repository.CharacterAiProfileRepository;
import com.example.umcCall.domain.character.repository.CharacterRepository;
import com.example.umcCall.domain.relationship.entity.Relationship;
import com.example.umcCall.domain.relationship.entity.RelationshipStatus;
import com.example.umcCall.domain.relationship.repository.RelationshipRepository;
import com.example.umcCall.domain.relationship.repository.RelationshipStatusRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 통화 한 턴의 AI 대화 오케스트레이션. STT final 텍스트 → AI 요청 조립 → chat() 호출.
 * <p>AI 경계 호출은 {@link AiConversationService}(client + stale 가드)에 맡기고, 여기선 통화 신원으로
 * 최신 스냅샷을 조립한다. <b>전사 DB 저장(후순위)이 나중에 이 서비스에 붙는다</b> — 그 자리가 여기다.
 * <p>조립엔 트랜잭션이 불필요하다: {@code findById}가 scalar 컬럼을 로드하고, 매퍼가 lazy 연관을 타지 않는다.
 */
@Service
@RequiredArgsConstructor
public class CallConversationService {

    /** AI에 보낼 맥락(history) 최대 개수. 채팅 {@code HISTORY_SIZE}와 동일하게 맞춰 일관성·지연·비용을 잡는다. */
    private static final int CONTEXT_HISTORY_SIZE = 20;

    private final CharacterRepository characterRepository;
    private final CharacterAiProfileRepository characterAiProfileRepository;
    private final RelationshipRepository relationshipRepository;
    private final RelationshipStatusRepository relationshipStatusRepository;
    private final AiCharacterSnapshotMapper characterSnapshotMapper;
    private final AiRelationshipSnapshotMapper relationshipSnapshotMapper;
    private final AiConversationService aiConversationService;

    /**
     * 통화 대화 로그를 AI로 넘겨 응답을 받는다.
     * <p>로그는 <b>이번 사용자 발화까지 포함</b>한 append-only 이벤트 로그다(호출부가 STT final 시 append).
     * AI 서버 계약은 {@code message}(이번 발화)와 {@code history}(이전 턴)를 분리해 받으므로,
     * 로그의 <b>마지막 항목을 message</b>로, 그 앞을 history로 파생해 보낸다 — 쪼개는 지점만 여기로 옮긴 것.
     *
     * @param conversation 이번 발화를 마지막에 포함한 대화 로그(비어 있으면 안 된다)
     * @return AI 응답. stale/AI 오류 시 {@code AiServerException}이 던져진다(호출부가 그 턴을 폐기).
     */
    public AiChatResponse respond(Long characterId, Long relationshipId,
                                  List<AiChatHistoryItem> conversation) {
        Character character = characterRepository.findById(characterId)
                .orElseThrow(() -> new IllegalStateException("Character not found: " + characterId));
        CharacterAiProfile profile = characterAiProfileRepository.findById(characterId)
                .orElseThrow(() -> new IllegalStateException("Character AI profile not found: " + characterId));
        Relationship relationship = relationshipRepository.findById(relationshipId)
                .orElseThrow(() -> new IllegalStateException("Relationship not found: " + relationshipId));
        RelationshipStatus status = relationshipStatusRepository.findByRelationshipId(relationshipId)
                .orElseThrow(() -> new IllegalStateException("Relationship status not found: " + relationshipId));

        int last = conversation.size() - 1;
        String message = conversation.get(last).content();
        // 맥락은 최근 CONTEXT_HISTORY_SIZE개로 윈도잉한다 — 채팅 시드 + 통화 턴이 쌓여도 매 턴 보낼 양을 상한한다
        // (지연·비용). subList는 뷰지만 AiChatRequest 생성자가 List.copyOf로 복사하므로 안전하다.
        int from = Math.max(0, last - CONTEXT_HISTORY_SIZE);
        List<AiChatHistoryItem> history = conversation.subList(from, last);

        AiChatRequest request = new AiChatRequest(
                UUID.randomUUID().toString(), // 멱등성 키. 턴마다 고유값(같은 값 재전송 시 409).
                characterId,
                message,
                characterSnapshotMapper.toSnapshot(character, profile, relationship),
                relationshipSnapshotMapper.toSnapshot(relationship, status),
                history);

        return aiConversationService.chat(request);
    }
}
