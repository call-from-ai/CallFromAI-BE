package com.example.umcCall.domain.relationship.repository;

import com.example.umcCall.domain.relationship.entity.ChatSummary;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatSummaryRepository extends JpaRepository<ChatSummary, Long> {

    Optional<ChatSummary> findByRelationshipId(Long relationshipId);

    void deleteByRelationshipId(Long relationshipId);
}
