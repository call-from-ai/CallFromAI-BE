package com.example.umcCall.domain.character.repository;

import com.example.umcCall.domain.character.entity.CharacterImage;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 캐릭터 프로필 사진 DB 조회/저장 담당.
 */
public interface CharacterImageRepository extends JpaRepository<CharacterImage, Long> {

    Optional<CharacterImage> findByCharacterId(Long characterId);

    void deleteByCharacterId(Long characterId);
}
