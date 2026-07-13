package com.example.umcCall.domain.member.service;

import com.example.umcCall.domain.member.dto.response.MemberResponse;
import com.example.umcCall.domain.member.dto.request.MemberUpdateRequest;
import com.example.umcCall.domain.member.entity.Member;
import com.example.umcCall.domain.member.repository.MemberRepository;
import com.example.umcCall.global.apiPayload.code.GeneralErrorCode;
import com.example.umcCall.global.exception.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;

    public MemberResponse getMyInfo(Long memberId) {
        Member member = findMember(memberId);
        return MemberResponse.from(member);
    }

    @Transactional
    public MemberResponse updateMyInfo(Long memberId, MemberUpdateRequest request) {
        Member member = findMember(memberId);
        member.updateProfile(
                request.lastName(), request.firstName(), request.profilePhotoUrl(),
                request.gender(), request.birth(), request.mbti(), request.job()
        );
        return MemberResponse.from(member);
    }

    @Transactional
    public void withdraw(Long memberId) {
        Member member = findMember(memberId);
        member.deactivate();
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BaseException(GeneralErrorCode.MEMBER_NOT_FOUND));
    }
}
