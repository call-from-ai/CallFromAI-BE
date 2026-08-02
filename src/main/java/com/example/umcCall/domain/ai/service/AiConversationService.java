package com.example.umcCall.domain.ai.service;

import com.example.umcCall.domain.ai.client.AiServerClient;
import com.example.umcCall.domain.ai.dto.AiChatRequest;
import com.example.umcCall.domain.ai.dto.AiChatResponse;
import com.example.umcCall.domain.ai.exception.AiErrorCode;
import com.example.umcCall.domain.ai.exception.AiServerException;
import com.example.umcCall.domain.relationship.repository.RelationshipRepository;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiConversationService {

    private final AiServerClient aiServerClient;
    private final RelationshipRepository relationshipRepository;

    public AiChatResponse chat(AiChatRequest request) {
        AiChatResponse response = aiServerClient.chat(request);

        Long relationshipId = request.relationship().relationshipId();
        Long requestedVersion = request.relationship().version();
        if (!relationshipRepository.existsByIdAndVersion(relationshipId, requestedVersion)) {
            // AI가 반환한 trust/repairProgress/breakupRisk/strategy를 포함해 응답 전체를 폐기한다.
            // 관계 delta를 합산하거나 AI 전용 값을 DB에 저장하지 않는다.
            throw new AiServerException(AiErrorCode.STALE_RELATIONSHIP);
        }
        return response;
    }

    /**
     * 스트리밍 대화(통화 TTFA 단축용). 대사 조각이 도착하는 대로 {@code onChunk}로 흘려보낸다.
     *
     * <p>⚠ <b>stale 가드가 {@link #chat}과 반대 순서다 — 여기선 스트림을 열기 <u>전에</u> 검사한다.</b>
     * {@link #chat}은 응답을 다 받은 뒤 판정해 그 턴을 통째로 폐기할 수 있지만, 스트리밍은 응답이 끝나기
     * 전에 이미 첫 문장이 소리로 나간다 — 나중에 판정해봐야 되돌릴 방법이 없다. 순서를 맞춘답시고
     * 뒤로 옮기면 가드가 무력화된다.
     *
     * <p>스트림이 도는 동안 관계가 바뀌는 경우까지는 막지 못한다(그 창은 몇 초다). 그건 원래
     * {@code chat()}도 못 막던 것이고, 가드의 목적은 <b>이미 낡은 스냅샷으로 시작하는 것</b>을 막는 데 있다.
     */
    public void chatStream(AiChatRequest request, Consumer<String> onChunk) {
        Long relationshipId = request.relationship().relationshipId();
        Long requestedVersion = request.relationship().version();
        if (!relationshipRepository.existsByIdAndVersion(relationshipId, requestedVersion)) {
            throw new AiServerException(AiErrorCode.STALE_RELATIONSHIP);
        }
        aiServerClient.chatStream(request, onChunk);
    }
}
