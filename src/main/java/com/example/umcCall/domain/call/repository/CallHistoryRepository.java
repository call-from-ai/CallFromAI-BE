package com.example.umcCall.domain.call.repository;

import com.example.umcCall.domain.call.entity.CallHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 통화 전사(transcript) 저장/조회 담당.
 */
public interface CallHistoryRepository extends JpaRepository<CallHistory, Long> {

    /** 통화의 전사 전체를 발화 순서(id ASC = 과거→최신)로 조회한다. */
    List<CallHistory> findByCallIdOrderByIdAsc(Long callId);
}
