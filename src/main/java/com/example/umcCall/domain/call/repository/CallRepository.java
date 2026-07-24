package com.example.umcCall.domain.call.repository;

import com.example.umcCall.domain.call.entity.Call;
import com.example.umcCall.domain.call.enums.CallSender;
import com.example.umcCall.domain.call.enums.CallStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 통화 기록 저장/조회 담당. 전사(transcript) 저장·통화 이력 조회가 여기에 붙는다.
 */
public interface CallRepository extends JpaRepository<Call, Long> {
    boolean existsByRelationshipIdAndStatusIn(Long relationshipId, Collection<CallStatus> statuses);

    long countByRelationshipIdAndSenderAndStatusInAndCreatedAtAfter(
            Long relationshipId,
            CallSender sender,
            Collection<CallStatus> statuses,
            LocalDateTime createdAt);
}
