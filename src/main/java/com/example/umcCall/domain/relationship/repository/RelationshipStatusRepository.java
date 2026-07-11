package com.example.umcCall.domain.relationship.repository;

import com.example.umcCall.domain.relationship.entity.RelationshipStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 관계 통계 DB 조회/저장 담당.
 */
public interface RelationshipStatusRepository extends JpaRepository<RelationshipStatus, Long> {

    Optional<RelationshipStatus> findByRelationshipId(Long relationshipId);

    void deleteByRelationshipId(Long relationshipId);
}
