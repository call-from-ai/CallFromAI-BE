package com.example.umcCall.domain.character.entity;

import com.example.umcCall.domain.character.enums.Gender;
import java.util.List;
import java.util.Map;

/**
 * 온보딩에서 선택 가능한 성별별 기본(프리셋) 프로필 이미지 URL 목록.
 */
public final class PresetImages {

    // TODO: 실제 S3 URL 확정되면 채워 넣기
    private static final Map<Gender, List<String>> IMAGES_BY_GENDER = Map.of(
            Gender.MALE, List.of(),
            Gender.FEMALE, List.of()
    );

    private PresetImages() {
    }

    public static List<String> of(Gender gender) {
        return IMAGES_BY_GENDER.getOrDefault(gender, List.of());
    }

    // 요청으로 들어온 imageUrl이 해당 성별의 프리셋 목록에 실제로 있는 값인지 검증
    public static boolean contains(Gender gender, String imageUrl) {
        return of(gender).contains(imageUrl);
    }
}
