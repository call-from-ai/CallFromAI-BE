package com.example.umcCall.domain.character.service;

import com.example.umcCall.domain.character.entity.CharacterTrait;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DefaultCharacterAiProfileCalculator implements CharacterAiProfileCalculator {

    private static final int CALCULATION_VERSION = 1;

    @Override
    public int calculationVersion() {
        return CALCULATION_VERSION;
    }

    @Override
    public CharacterAiProfileScores calculate(List<CharacterTrait> traits) {
        // 입력 순서와 무관하게 동일한 결과가 나오도록 정렬한다.
        List<CharacterTrait> orderedTraits = traits.stream()
                .sorted(Comparator.comparing(CharacterTrait::getPriority)
                        .thenComparing(trait -> trait.getTrait().name()))
                .toList();

        // TODO: 팀 확정 후 trait/priority별 실제 가중치와 mind/responseStyle/lifeType 규칙 적용.
        // 가중치 확정 전에는 결정적인 중립값을 저장하며, 규칙 변경 시 CALCULATION_VERSION을 올린다.
        if (orderedTraits.isEmpty()) {
            return CharacterAiProfileScores.baseline();
        }
        return CharacterAiProfileScores.baseline();
    }
}
