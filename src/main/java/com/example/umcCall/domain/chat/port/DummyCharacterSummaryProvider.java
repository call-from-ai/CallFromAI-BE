package com.example.umcCall.domain.chat.port;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 캐릭터 도메인이 아직 없을 때 사용하는 더미 구현체.
 * relationshipId 기반으로 가짜 이름/사진을 만들고, 정렬 검증을 위해
 * "가장 작은 relationshipId 하나"를 메인 연인으로 표시한다.
 */
@Component
public class DummyCharacterSummaryProvider implements CharacterSummaryProvider {

    @Override
    public Map<Long, CharacterSummary> getSummaries(Collection<Long> relationshipIds) {
        if (relationshipIds == null || relationshipIds.isEmpty()) {
            return Map.of();
        }

        // 정렬 검증용: 가장 작은 id를 메인 연인으로 지정
        Long mainId = relationshipIds.stream().min(Comparator.naturalOrder()).orElse(null);

        Map<Long, CharacterSummary> result = new HashMap<>();
        for (Long relationshipId : relationshipIds) {
            result.put(relationshipId, new CharacterSummary(
                    "더미캐릭터" + relationshipId,
                    "https://dummy.image/character/" + relationshipId + ".png",
                    relationshipId.equals(mainId)
            ));
        }
        return result;
    }
}
