package com.example.umcCall.domain.ai.service;

import com.example.umcCall.domain.ai.client.AiServerClient;
import com.example.umcCall.domain.ai.dto.AiChatRequest;
import com.example.umcCall.domain.ai.dto.AiChatResponse;
import com.example.umcCall.domain.ai.exception.AiErrorCode;
import com.example.umcCall.domain.ai.exception.AiServerException;
import com.example.umcCall.domain.relationship.repository.RelationshipRepository;
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
}
