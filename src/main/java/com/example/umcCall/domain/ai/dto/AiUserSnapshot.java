package com.example.umcCall.domain.ai.dto;

import java.time.LocalDate;

/** AI 대화 생성에 필요한 사용자 프로필 스냅샷. */
public record AiUserSnapshot(
        LocalDate birth,
        String job,
        String mbti
) {
}
