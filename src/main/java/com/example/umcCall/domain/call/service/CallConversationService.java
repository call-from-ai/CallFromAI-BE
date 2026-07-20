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

    private final CharacterRepository characterRepository;
    private final CharacterAiProfileRepository characterAiProfileRepository;
    private final RelationshipRepository relationshipRepository;
    private final RelationshipStatusRepository relationshipStatusRepository;
    private final AiCharacterSnapshotMapper characterSnapshotMapper;
    private final AiRelationshipSnapshotMapper relationshipSnapshotMapper;
    private final AiConversationService aiConversationService;

    /**
     * 사용자 발화 한 턴을 AI로 넘겨 응답을 받는다.
     *
     * @param history 이전 턴들(이번 {@code message}는 포함하지 않는다)
     * @return AI 응답. stale/AI 오류 시 {@code AiServerException}이 던져진다(호출부가 그 턴을 폐기).
     */
    public AiChatResponse respond(Long characterId, Long relationshipId,
                                  String message, List<AiChatHistoryItem> history) {
        Character character = characterRepository.findById(characterId)
                .orElseThrow(() -> new IllegalStateException("Character not found: " + characterId));
        CharacterAiProfile profile = characterAiProfileRepository.findById(characterId)
                .orElseThrow(() -> new IllegalStateException("Character AI profile not found: " + characterId));
        Relationship relationship = relationshipRepository.findById(relationshipId)
                .orElseThrow(() -> new IllegalStateException("Relationship not found: " + relationshipId));
        RelationshipStatus status = relationshipStatusRepository.findByRelationshipId(relationshipId)
                .orElseThrow(() -> new IllegalStateException("Relationship status not found: " + relationshipId));

        AiChatRequest request = new AiChatRequest(
                characterId,
                message,
                characterSnapshotMapper.toSnapshot(character, profile, relationship),
                relationshipSnapshotMapper.toSnapshot(relationship, status),
                history);

        return aiConversationService.chat(request);
    }
}
