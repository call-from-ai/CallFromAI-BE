package com.example.umcCall.domain.call.repository;

import com.example.umcCall.domain.call.entity.CallHistory;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 통화 전사(transcript) 저장 담당. 지금은 write 전용 — 전사 열람(조회) API는 별개 작업(후순위).
 */
public interface CallHistoryRepository extends JpaRepository<CallHistory, Long> {
}
