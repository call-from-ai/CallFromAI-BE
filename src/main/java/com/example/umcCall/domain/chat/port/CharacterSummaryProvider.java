package com.example.umcCall.domain.chat.port;

import java.util.Collection;
import java.util.Map;

/**
 * 채팅 도메인이 소유하는 아웃바운드 포트.
 * relationshipId 목록을 주면 캐릭터 요약(이름/사진/메인여부)을 돌려주셈
 *
 * 지금은 {@link DummyCharacterSummaryProvider}가 더미로 구현하고,
 * 용환/현경님 레포지토리가 준비되면 실제 구현체를 만들어 @Primary로 교체한다.
 * 이 인터페이스에 의존하는 서비스 코드는 교체 시에도 바뀌지 않는다.
 */
public interface CharacterSummaryProvider {

    /**
     * @param relationshipIds 조회할 관계 ID 목록(중복/순서 무관, null 요소 없음)
     * @return relationshipId -> 캐릭터 요약. 존재하지 않는 id는 결과에서 빠질 수 있다.
     */
    Map<Long, CharacterSummary> getSummaries(Collection<Long> relationshipIds);
}
