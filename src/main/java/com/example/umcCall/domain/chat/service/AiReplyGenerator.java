package com.example.umcCall.domain.chat.service;

import com.example.umcCall.domain.ai.dto.AiChatHistoryItem;
import com.example.umcCall.domain.ai.dto.AiChatRequest;
import com.example.umcCall.domain.ai.dto.AiChatResponse;
import com.example.umcCall.domain.ai.dto.AiCharacterSnapshot;
import com.example.umcCall.domain.ai.dto.AiRelationshipSnapshot;
import com.example.umcCall.domain.ai.mapper.AiCharacterSnapshotMapper;
import com.example.umcCall.domain.ai.mapper.AiRelationshipSnapshotMapper;
import com.example.umcCall.domain.ai.service.AiConversationService;
import com.example.umcCall.domain.character.entity.Character;
import com.example.umcCall.domain.character.entity.CharacterAiProfile;
import com.example.umcCall.domain.character.repository.CharacterAiProfileRepository;
import com.example.umcCall.domain.relationship.entity.Relationship;
import com.example.umcCall.domain.relationship.entity.RelationshipStatus;
import com.example.umcCall.domain.relationship.repository.RelationshipRepository;
import com.example.umcCall.domain.relationship.repository.RelationshipStatusRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 채팅방 정보로 AI 서버에 답장을 요청해 텍스트를 받아오는 컴포넌트
 */
@Component
@RequiredArgsConstructor
public class AiReplyGenerator {

    private final RelationshipRepository relationshipRepository;
    private final CharacterAiProfileRepository characterAiProfileRepository;
    private final RelationshipStatusRepository relationshipStatusRepository;
    private final AiCharacterSnapshotMapper characterSnapshotMapper;
    private final AiRelationshipSnapshotMapper relationshipSnapshotMapper;
    private final AiConversationService aiConversationService;

    /**
     * relationshipId + 유저 메시지 + 대화 이력으로 AI 서버를 호출해 답장 텍스트를 반환한다.
     */
    @Transactional(readOnly = true)
    public String generateReply(Long relationshipId, String userMessage, List<AiChatHistoryItem> history) {
        Relationship relationship = relationshipRepository.findById(relationshipId)
                .orElseThrow(() -> new IllegalStateException("관계를 찾을 수 없습니다: " + relationshipId));
        Character character = relationship.getCharacter();
        CharacterAiProfile profile = characterAiProfileRepository.findById(character.getId())
                .orElseThrow(() -> new IllegalStateException("캐릭터 AI 프로필이 없습니다: " + character.getId()));
        RelationshipStatus status = relationshipStatusRepository.findByRelationshipId(relationshipId)
                .orElseThrow(() -> new IllegalStateException("관계 통계가 없습니다: " + relationshipId));

        AiCharacterSnapshot characterSnapshot = characterSnapshotMapper.toSnapshot(character, profile, relationship);
        AiRelationshipSnapshot relationshipSnapshot = relationshipSnapshotMapper.toSnapshot(relationship, status);

        AiChatRequest request = new AiChatRequest(
                character.getId(), userMessage, characterSnapshot, relationshipSnapshot, history);
        AiChatResponse response = aiConversationService.chat(request);
        return response.message();
    }
}
