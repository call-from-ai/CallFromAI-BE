package com.example.umcCall.domain.ai.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;

/** AI 대화 생성에 필요한 사용자 프로필 스냅샷. */
public record AiUserSnapshot(
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate birth,
        String gender,
        String job,
        String mbti
) {
}
