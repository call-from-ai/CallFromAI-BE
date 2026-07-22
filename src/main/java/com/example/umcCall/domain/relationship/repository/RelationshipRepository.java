package com.example.umcCall.domain.relationship.repository;

import com.example.umcCall.domain.relationship.entity.Relationship;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 회원-캐릭터 관계 DB 조회/저장 담당. 캐릭터 소유권/목록 조회는 여기를 거쳐서 한다.
 */
public interface RelationshipRepository extends JpaRepository<Relationship, Long> {

    boolean existsByIdAndVersion(Long id, Long version);

    Optional<Relationship> findByCharacterId(Long characterId);
    Optional<Relationship> findByCharacterIdAndCharacterDeletedAtIsNull(Long characterId);

    Optional<Relationship> findByMemberIdAndMainTrue(Long memberId);
    Optional<Relationship> findByMemberIdAndMainTrueAndCharacterDeletedAtIsNull(Long memberId);

    List<Relationship> findByMemberId(Long memberId);
    List<Relationship> findByMemberIdAndCharacterDeletedAtIsNull(Long memberId);

    int countByMemberId(Long memberId);
    int countByMemberIdAndCharacterDeletedAtIsNull(Long memberId);
}
