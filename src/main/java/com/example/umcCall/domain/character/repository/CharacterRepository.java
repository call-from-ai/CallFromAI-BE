package com.example.umcCall.domain.character.repository;

import com.example.umcCall.domain.character.entity.Character;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 캐릭터 DB 조회/저장 담당. 소유권(memberId)은 character가 아니라 relationship이 가지고 있어서
 * 회원 기준 조회는 RelationshipRepository를 거쳐서 한다.
 */
public interface CharacterRepository extends JpaRepository<Character, Long> {
}
