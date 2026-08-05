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
        @Schema(example = "김", description = "성(2자 이내)")
        @NotBlank(message = "성은 필수입니다.")
        @Size(max = 2, message = "성은 2자 이내로 입력해주세요.")
        String lastName,

        @Schema(example = "민준", description = "이름(5자 이내)")
        @NotBlank(message = "이름은 필수입니다.")
        @Size(max = 5, message = "이름은 5자 이내로 입력해주세요.")
        String firstName,

        @Schema(example = "https://callfromai-images.s3.ap-northeast-2.amazonaws.com/male_1.png",
                description = "프로필 사진 URL. GET /preset-images로 조회한 프리셋 이미지 URL 중 하나여야 함")
        @NotBlank(message = "프로필 사진은 필수입니다.")
        String imageUrl,

        @Schema(example = "MALE", description = "성별: MALE(남성) 또는 FEMALE(여성)")
        @NotNull(message = "성별은 필수입니다.")
        Gender gender,

        @Schema(example = "2000-01-01", description = "생년월일 (yyyy-MM-dd 형식)")
        @NotNull(message = "생년월일은 필수입니다.")
        LocalDate birth,

        @Schema(example = "INTJ", description = "MBTI 16가지 유형 중 하나")
        @NotNull(message = "MBTI는 필수입니다.")
        Mbti mbti,

        @Schema(example = "UNIVERSITY_STUDENT", description = "직업: UNIVERSITY_STUDENT(대학생), EMPLOYEE(직장인), OTHER(기타)")
        @NotNull(message = "직업은 필수입니다.")
        Job job
) {}