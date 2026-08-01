package com.example.umcCall.domain.member.service;

import com.example.umcCall.domain.auth.repository.RefreshTokenRepository;
import com.example.umcCall.domain.member.dto.request.DoNotDisturbUpdateRequest;
import com.example.umcCall.domain.member.dto.request.NotificationSettingUpdateRequest;
import com.example.umcCall.domain.member.dto.response.MemberResponse;
import com.example.umcCall.domain.member.dto.request.MemberUpdateRequest;
import com.example.umcCall.domain.member.dto.response.NotificationSettingResponse;
import com.example.umcCall.domain.member.entity.Member;
import com.example.umcCall.domain.member.exception.MemberErrorCode;
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
    private final RefreshTokenRepository refreshTokenRepository;
    private final CharacterService characterService;

    public MemberResponse getMyInfo(Long memberId) {
        Member member = findMember(memberId);
        return MemberResponse.from(member);
    }

    @Transactional
    public MemberResponse updateMyInfo(Long memberId, MemberUpdateRequest request) {
        Member member = findMember(memberId);
        member.updateProfile(
                request.lastName(), request.firstName(), request.imageUrl(),
                request.gender(), request.birth(), request.mbti(), request.job()
        );
        return MemberResponse.from(member);
    }

    @Transactional
    public void withdraw(Long memberId) {
        Member member = findMember(memberId);
        characterService.deleteAllCharactersForWithdraw(memberId);
        member.deactivate();
        // 탈퇴 처리와 refreshToken DB 삭제
        refreshTokenRepository.findByMemberId(memberId)
                .ifPresent(refreshTokenRepository::delete);
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BaseException(MemberErrorCode.MEMBER_NOT_FOUND));
    }

    public NotificationSettingResponse getNotificationSetting(Long memberId) {
        Member member = findMember(memberId);
        return NotificationSettingResponse.from(member);
    }

    @Transactional
    public NotificationSettingResponse updateNotificationSetting(Long memberId, NotificationSettingUpdateRequest request) {
        Member member = findMember(memberId);
        member.updateNotificationSetting(request.allNotificationEnabled(), request.nightCallAllowed());
        return NotificationSettingResponse.from(member);
    }

    @Transactional
    public NotificationSettingResponse updateDoNotDisturb(Long memberId, DoNotDisturbUpdateRequest request) {
        Member member = findMember(memberId);
        member.updateDoNotDisturb(request.startTime(), request.endTime());
        return NotificationSettingResponse.from(member);
    }
}
