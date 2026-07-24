package com.example.umcCall.domain.call.repository;

import com.example.umcCall.domain.call.dto.response.CallListItem;
import com.example.umcCall.domain.call.entity.Call;
import com.example.umcCall.domain.call.enums.CallStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 통화 기록 저장/조회 담당. 전사(transcript) 저장·통화 이력 조회가 여기에 붙는다.
 */
public interface CallRepository extends JpaRepository<Call, Long> {

    /**
     * 회원의 통화 목록을 최신순(createdAt DESC)으로 조회한다. 상대 캐릭터 이름까지 한 번에 조인해
     * DTO로 프로젝션한다(N+1·lazy 회피). 개수 제한·상태 필터는 호출부가 준다.
     */
    @Query("""
            select new com.example.umcCall.domain.call.dto.response.CallListItem(
                c.id, ch.firstName, c.sender, c.aiSummary, c.createdAt, c.status)
            from Call c
                join c.relationship r
                join r.character ch
            where r.memberId = :memberId
              and c.status in :statuses
            order by c.createdAt desc
            """)
    List<CallListItem> findRecentCallList(@Param("memberId") Long memberId,
                                          @Param("statuses") Collection<CallStatus> statuses,
                                          Pageable pageable);
}
