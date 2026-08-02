package com.example.umcCall.domain.member.dto.request;

import com.example.umcCall.domain.image.enums.Gender;
import com.example.umcCall.domain.member.enums.Job;
import com.example.umcCall.domain.member.enums.Mbti;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

public record MemberUpdateRequest(
        @Schema(example = "김") String lastName,
        @Schema(example = "민준") String firstName,
        @Schema(example = "https://callfromai-images.s3.ap-northeast-2.amazonaws.com/male_1.png") String imageUrl,
        @Schema(example = "MALE") Gender gender,
        @Schema(example = "2000-01-01") LocalDate birth,
        @Schema(example = "INTJ") Mbti mbti,
        @Schema(example = "UNIVERSITY_STUDENT") Job job
) {}