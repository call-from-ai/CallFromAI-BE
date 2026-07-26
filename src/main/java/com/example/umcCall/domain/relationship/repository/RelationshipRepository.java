package com.example.umcCall.domain.relationship.repository;

import com.example.umcCall.domain.relationship.entity.Relationship;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 회원-캐릭터 관계 DB 조회/저장 담당. 캐릭터 소유권/목록 조회는 여기를 거쳐서 한다.
 */
public interface RelationshipRepository extends JpaRepository<Relationship, Long> {

    /**
     * 관계 행을 비관적 락으로 집는다. 같은 관계에 통화가 둘 생기는 걸 막는 임계 구역의 열쇠다 —
     * 사용자 발신(dial)과 예약 발신(fire)이 각자 활성 통화를 확인하고 저장하기 때문이다.
     * <p>Call이 아니라 관계를 잠그는 이유: 막으려는 대상이 "이 관계에 통화가 이미 있는가"라서
     * 아직 존재하지 않는 행은 잠글 수 없다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Relationship r where r.id = :id")
    Optional<Relationship> findByIdForUpdate(@Param("id") Long id);

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
