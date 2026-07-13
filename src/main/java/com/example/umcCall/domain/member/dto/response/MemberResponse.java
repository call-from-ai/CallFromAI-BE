package com.example.umcCall.domain.member.dto.response;

import com.example.umcCall.domain.member.entity.Member;
import com.example.umcCall.domain.member.enums.Gender;
import com.example.umcCall.domain.member.enums.Job;
import com.example.umcCall.domain.member.enums.Mbti;

import java.time.LocalDate;

public record MemberResponse(
        Long memberId,
        String lastName,
        String firstName,
        String profilePhotoUrl,
        Gender gender,
        LocalDate birth,
        Mbti mbti,
        Job job,
        boolean needsOnboarding
) {
    public static MemberResponse from(Member member) {
        return new MemberResponse(
                member.getId(),
                member.getLastName(),
                member.getFirstName(),
                member.getProfilePhotoUrl(),
                member.getGender(),
                member.getBirth(),
                member.getMbti(),
                member.getJob(),
                !member.isOnboardingCompleted()
        );
    }
}
