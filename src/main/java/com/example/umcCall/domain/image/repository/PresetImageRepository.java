package com.example.umcCall.domain.image.repository;

import com.example.umcCall.domain.image.entity.PresetImage;
import com.example.umcCall.domain.image.enums.Gender;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PresetImageRepository extends JpaRepository<PresetImage, Long> {
    List<PresetImage> findByGender(Gender gender);
    boolean existsByGenderAndImageUrl(Gender gender, String imageUrl);

    /**
     * 캐릭터가 고른 프리셋을 되찾는다(목소리 해석용).
     * <p>⚠ 조회 키가 <b>(성별 + URL 문자열)</b>인 이유: {@code Character}는 프리셋 FK가 아니라 {@code imageUrl}
     * 문자열을 복사해 들고 있고, 생성·수정 때 {@link #existsByGenderAndImageUrl}로 이 쌍을 검증한다.
     * 즉 실질 매칭 키가 이 둘이다.
     * <p>⚠ 프리셋 이미지의 S3 경로가 바뀌면 매칭이 <b>에러 없이 조용히</b> 끊겨 전부 기본 목소리로 떨어진다.
     * 프리셋 URL을 손댈 일이 생기면 통화 음성 매핑을 같이 확인할 것.
     */
    Optional<PresetImage> findByGenderAndImageUrl(Gender gender, String imageUrl);
}