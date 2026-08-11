package com.example.umcCall.domain.call.service;

import com.example.umcCall.domain.ai.dto.AiChatHistoryItem;
import com.example.umcCall.domain.ai.dto.AiChatRequest;
import com.example.umcCall.domain.ai.enums.AiConversationChannel;
import com.example.umcCall.domain.ai.mapper.AiCharacterSnapshotMapper;
import com.example.umcCall.domain.ai.mapper.AiRelationshipSnapshotMapper;
import com.example.umcCall.domain.ai.service.AiRequestContextProvider;
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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 통화 신원(characterId/relationshipId)으로 최신 스냅샷을 읽어 AI 요청 하나를 조립한다.
 *
 * <p><b>왜 별도 빈인가 — 트랜잭션 경계를 조립에만 두려고.</b> 통화 워커는 웹 요청 스레드가 아니라
 * OSIV가 없고, {@link CallConversationService#respondStream}은 조립 직후 <b>수 초짜리 SSE 스트림</b>을
 * 연다. 조립과 스트림이 한 메서드에 있으면 트랜잭션을 걸 자리가 없다 — 메서드에 걸면 스트림이 도는 내내
 * DB 커넥션을 잡아 풀이 고갈되고(⚠ 절대 금지), 안 걸면 매퍼가 lazy 연관에 손대는 순간
 * {@code LazyInitializationException}이 <b>통화 도중</b> 터진다.
 *
 * <p>그래서 조립만 떼어 {@code readOnly} 트랜잭션에 넣었다. 여기서 도는 건 findById 4방과 매퍼 호출뿐이라
 * 외부 I/O가 없고, 커넥션 점유는 밀리초 단위다.
 *
 * <p>⚠ 매퍼({@link AiCharacterSnapshotMapper}·{@link AiRelationshipSnapshotMapper})는 <b>채팅·선발신·
 * 캐릭터 동기화가 함께 쓰는 계약 표현</b>이라, 통화 사정으로 시그니처를 스칼라 파라미터로 바꾸지 않았다.
 * 대신 이 경계가 "매퍼가 무엇을 만지든 안전하다"를 보장한다.
 */
@Component
@RequiredArgsConstructor
public class CallAiRequestAssembler {

    /** AI에 보낼 맥락(history) 최대 개수. 채팅 {@code HISTORY_SIZE}와 동일하게 맞춰 일관성·지연·비용을 잡는다. */
    private static final int CONTEXT_HISTORY_SIZE = 20;

    private final CharacterRepository characterRepository;
    private final CharacterAiProfileRepository characterAiProfileRepository;
    private final RelationshipRepository relationshipRepository;
    private final RelationshipStatusRepository relationshipStatusRepository;
    private final AiCharacterSnapshotMapper characterSnapshotMapper;
    private final AiRelationshipSnapshotMapper relationshipSnapshotMapper;
    private final AiRequestContextProvider requestContextProvider;

    /**
     * 대화 로그를 AI 요청으로 만든다.
     *
     * <p>AI 서버 계약은 {@code message}(이번 발화)와 {@code history}(이전 턴)를 분리해 받으므로,
     * 로그의 <b>마지막 항목을 message</b>로, 그 앞을 history로 파생한다.
     *
     * @param conversation 이번 발화를 마지막에 포함한 대화 로그(비어 있으면 안 된다)
     */
    @Transactional(readOnly = true)
    public AiChatRequest assemble(Long characterId, Long relationshipId,
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
        // 맥락은 최근 CONTEXT_HISTORY_SIZE개로 윈도잉한다 — 통화 턴이 쌓여도 매 턴 보낼 양을 상한한다
        // (지연·비용). subList는 뷰지만 AiChatRequest 생성자가 List.copyOf로 복사하므로 안전하다.
        int from = Math.max(0, last - CONTEXT_HISTORY_SIZE);
        List<AiChatHistoryItem> history = conversation.subList(from, last);
        AiRequestContextProvider.Context requestContext = requestContextProvider.create(relationship.getMemberId());

        return new AiChatRequest(
                UUID.randomUUID().toString(), // 멱등성 키. 턴마다 고유값(같은 값 재전송 시 409).
                characterId,
                AiConversationChannel.CALL,
                requestContext.userName(),
                requestContext.userTimeZone(),
                requestContext.localDateTime(),
                requestContext.user(),
                message,
                characterSnapshotMapper.toSnapshot(character, profile, relationship),
                relationshipSnapshotMapper.toSnapshot(relationship, status),
                history);
    }
}
