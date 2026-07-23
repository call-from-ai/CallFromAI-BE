package com.example.umcCall.domain.call.repository;

import com.example.umcCall.domain.call.entity.Call;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 통화 기록 저장/조회 담당. 전사(transcript) 저장·통화 이력 조회가 여기에 붙는다.
 */
public interface CallRepository extends JpaRepository<Call, Long> {
}
