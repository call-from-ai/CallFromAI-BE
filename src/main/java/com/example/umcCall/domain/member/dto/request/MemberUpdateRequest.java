package com.example.umcCall.domain.member.dto.request;

import com.example.umcCall.domain.member.enums.Gender;
import com.example.umcCall.domain.member.enums.Job;
import com.example.umcCall.domain.member.enums.Mbti;

import java.time.LocalDate;

public record MemberUpdateRequest(
        String lastName,
        String firstName,
        String profilePhotoUrl,
        Gender gender,
        LocalDate birth,
        Mbti mbti,
        Job job
) {}
