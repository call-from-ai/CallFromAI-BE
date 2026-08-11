package com.example.umcCall.domain.chat.service;

import com.example.umcCall.domain.ai.dto.AiChatHistoryItem;
import com.example.umcCall.domain.ai.dto.AiChatRequest;
import com.example.umcCall.domain.ai.dto.AiCharacterSnapshot;
import com.example.umcCall.domain.ai.dto.AiRelationshipSnapshot;
import com.example.umcCall.domain.ai.enums.AiConversationChannel;
import com.example.umcCall.domain.ai.mapper.AiCharacterSnapshotMapper;
import com.example.umcCall.domain.ai.mapper.AiRelationshipSnapshotMapper;
import com.example.umcCall.domain.ai.service.AiRequestContextProvider;
import com.example.umcCall.domain.character.entity.Character;
import com.example.umcCall.domain.character.entity.CharacterAiProfile;
import com.example.umcCall.domain.character.repository.CharacterAiProfileRepository;
import com.example.umcCall.domain.relationship.entity.Relationship;
import com.example.umcCall.domain.relationship.entity.RelationshipStatus;
import com.example.umcCall.domain.relationship.repository.RelationshipRepository;
import com.example.umcCall.domain.relationship.repository.RelationshipStatusRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 서버로 보낼 요청(AiChatRequest)을 조립하는 컴포넌트.
 * DB 조회와 스냅샷 조립까지만 트랜잭션 안에서 끝내고 순수 DTO를 반환한다.
 * 느린 AI REST 호출은 호출부가 트랜잭션 밖에서 하도록 분리해, DB 커넥션을 오래 쥐지 않는다.
 * 지연로딩(relationship.getCharacter 등)은 반드시 이 트랜잭션 안에서 모두 꺼내 DTO로 만든다.
 */
@Component
@RequiredArgsConstructor
public class AiReplyGenerator {

    private final RelationshipRepository relationshipRepository;
    private final CharacterAiProfileRepository characterAiProfileRepository;
    private final RelationshipStatusRepository relationshipStatusRepository;
    private final AiCharacterSnapshotMapper characterSnapshotMapper;
    private final AiRelationshipSnapshotMapper relationshipSnapshotMapper;
    private final AiRequestContextProvider requestContextProvider;

    /**
     * relationshipId + 유저 메시지 + 대화 이력으로 AI 서버 요청 DTO를 만든다.
     */
    @Transactional(readOnly = true)
    public AiChatRequest buildRequest(Long relationshipId, String userMessage, List<AiChatHistoryItem> history) {
        Relationship relationship = relationshipRepository.findById(relationshipId)
                .orElseThrow(() -> new IllegalStateException("관계를 찾을 수 없습니다: " + relationshipId));
        Character character = relationship.getCharacter();
        CharacterAiProfile profile = characterAiProfileRepository.findById(character.getId())
                .orElseThrow(() -> new IllegalStateException("캐릭터 AI 프로필이 없습니다: " + character.getId()));
        RelationshipStatus status = relationshipStatusRepository.findByRelationshipId(relationshipId)
                .orElseThrow(() -> new IllegalStateException("관계 통계가 없습니다: " + relationshipId));

        AiCharacterSnapshot characterSnapshot = characterSnapshotMapper.toSnapshot(character, profile, relationship);
        AiRelationshipSnapshot relationshipSnapshot = relationshipSnapshotMapper.toSnapshot(relationship, status);
        AiRequestContextProvider.Context requestContext = requestContextProvider.create(relationship.getMemberId());

        return new AiChatRequest(
                UUID.randomUUID().toString(),   // 멱등성 키. 논리 요청마다 고유값(같은 값 재전송 시 AI 서버가 409)
                character.getId(), AiConversationChannel.CHAT,
                requestContext.userName(), requestContext.userTimeZone(), requestContext.localDateTime(),
                requestContext.user(),
                userMessage,
                characterSnapshot, relationshipSnapshot, history);
    }
}
