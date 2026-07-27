package com.example.umcCall.domain.proactive.repository;

import com.example.umcCall.domain.proactive.entity.ProactiveContactSchedule;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProactiveContactScheduleRepository extends JpaRepository<ProactiveContactSchedule, Long> {

    Optional<ProactiveContactSchedule> findByRelationshipId(Long relationshipId);

    void deleteByRelationshipId(Long relationshipId);

    @Query("""
            select s.id from ProactiveContactSchedule s
            where s.enabled = true
              and s.relationship.character.deletedAt is null
              and ((s.nextCheckAt is not null and s.nextCheckAt <= :now)
                or (s.pendingRequestId is not null and s.pendingRetryAt <= :now))
            order by coalesce(s.pendingRetryAt, s.nextCheckAt), s.id
            """)
    List<Long> findDueIds(@Param("now") LocalDateTime now, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ProactiveContactSchedule s where s.id = :id")
    Optional<ProactiveContactSchedule> findByIdForUpdate(@Param("id") Long id);
}
