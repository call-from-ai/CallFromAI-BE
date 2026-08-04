package com.example.umcCall.domain.member.dto.request;

import com.example.umcCall.domain.image.enums.Gender;
import com.example.umcCall.domain.member.enums.Job;
import com.example.umcCall.domain.member.enums.Mbti;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record MemberCreateRequest(
        @Schema(example = "김")
        @NotBlank(message = "성은 필수입니다.")
        @Size(max = 2, message = "성은 2자 이내로 입력해주세요.")
        String lastName,

        @Schema(example = "민준")
        @NotBlank(message = "이름은 필수입니다.")
        @Size(max = 5, message = "이름은 5자 이내로 입력해주세요.")
        String firstName,

        @Schema(example = "https://callfromai-images.s3.ap-northeast-2.amazonaws.com/male_1.png")
        @NotBlank(message = "프로필 사진은 필수입니다.")
        String imageUrl,

        @Schema(example = "MALE")
        @NotNull(message = "성별은 필수입니다.")
        Gender gender,

        @Schema(example = "2000-01-01")
        @NotNull(message = "생년월일은 필수입니다.")
        LocalDate birth,

        @Schema(example = "INTJ")
        @NotNull(message = "MBTI는 필수입니다.")
        Mbti mbti,

        @Schema(example = "UNIVERSITY_STUDENT")
        @NotNull(message = "직업은 필수입니다.")
        Job job
) {
    public MemberUpdateRequest toUpdateRequest() {
        return new MemberUpdateRequest(lastName, firstName, imageUrl, gender, birth, mbti, job);
    }
}