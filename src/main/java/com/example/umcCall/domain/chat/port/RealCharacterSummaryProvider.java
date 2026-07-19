package com.example.umcCall.domain.chat.port;

import com.example.umcCall.domain.character.entity.Character;
import com.example.umcCall.domain.relationship.entity.Relationship;
import com.example.umcCall.domain.relationship.repository.RelationshipRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 채팅방 목록용 캐릭터 요약 실제 구현체.
 */
@Component
@RequiredArgsConstructor
public class RealCharacterSummaryProvider implements CharacterSummaryProvider {

    private final RelationshipRepository relationshipRepository;

    @Override
    @Transactional(readOnly = true)
    public Map<Long, CharacterSummary> getSummaries(Collection<Long> relationshipIds) {
        if (relationshipIds == null || relationshipIds.isEmpty()) {
            return Map.of();
        }

        List<Relationship> relationships = relationshipRepository.findAllById(relationshipIds);

        Map<Long, CharacterSummary> result = new HashMap<>();
        for (Relationship relationship : relationships) {
            Character character = relationship.getCharacter();
            result.put(relationship.getId(), new CharacterSummary(
                    character.getFirstName(),   // 이름(firstName)만
                    character.getImageUrl(),
                    relationship.isMain()
            ));
        }
        return result;
    }
}
