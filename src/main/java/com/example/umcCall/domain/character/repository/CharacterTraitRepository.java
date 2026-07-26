package com.example.umcCall.domain.character.repository;

import com.example.umcCall.domain.character.entity.CharacterTrait;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 캐릭터-매력 키워드 매핑 DB 조회/저장 담당.
 */
public interface CharacterTraitRepository extends JpaRepository<CharacterTrait, Long> {

    List<CharacterTrait> findByCharacterIdOrderByPriorityAsc(Long characterId);
    void deleteByCharacterId(Long characterId);
}
