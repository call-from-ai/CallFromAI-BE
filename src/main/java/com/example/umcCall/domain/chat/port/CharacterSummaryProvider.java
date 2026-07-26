package com.example.umcCall.domain.chat.port;

import java.util.Collection;
import java.util.Map;

/**
 * 채팅 도메인이 소유하는 아웃바운드 포트. 채팅방 목록에 쓰임
 * relationshipId 목록을 주면 캐릭터 요약(이름/사진/메인여부)을 돌려주셈
 */
public interface CharacterSummaryProvider {

    /**
     * @param relationshipIds 조회할 관계 ID 목록(중복/순서 무관, null 요소 없음)
     * @return relationshipId -> 캐릭터 요약. 존재하지 않는 id는 결과에서 빠질 수 있다.
     */
    Map<Long, CharacterSummary> getSummaries(Collection<Long> relationshipIds);
}
