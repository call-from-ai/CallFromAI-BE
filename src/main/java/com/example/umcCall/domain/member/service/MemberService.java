package com.example.umcCall.domain.member.service;

import com.example.umcCall.domain.auth.repository.RefreshTokenRepository;
import com.example.umcCall.domain.character.service.CharacterService;
import com.example.umcCall.domain.image.enums.Gender;
import com.example.umcCall.domain.image.repository.PresetImageRepository;
import com.example.umcCall.domain.member.dto.request.DoNotDisturbUpdateRequest;
import com.example.umcCall.domain.member.dto.request.MemberCreateRequest;
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
    private final PresetImageRepository presetImageRepository;

    public MemberResponse getMyInfo(Long memberId) {
        Member member = findMember(memberId);
        return MemberResponse.from(member);
    }

    @Transactional
    public MemberResponse createMyInfo(Long memberId, MemberCreateRequest request) {
        Member member = findMember(memberId);

        if (member.isOnboardingCompleted()) {
            throw new BaseException(MemberErrorCode.MEMBER_INFO_ALREADY_REGISTERED);
        }

        if (request.gender() != null && request.imageUrl() != null
                && !presetImageRepository.existsByGenderAndImageUrl(request.gender(), request.imageUrl())) {
            throw new BaseException(MemberErrorCode.INVALID_PRESET_IMAGE);
        }

        member.updateProfile(
                request.lastName(), request.firstName(), request.imageUrl(),
                request.gender(), request.birth(), request.mbti(), request.job()
        );

        return MemberResponse.from(member);
    }

    @Transactional
    public MemberResponse updateMyInfo(Long memberId, MemberUpdateRequest request) {
        Member member = findMember(memberId);

        // 생성 전 수정 호출 금지
        if (!member.isOnboardingCompleted()) {
            throw new BaseException(MemberErrorCode.MEMBER_INFO_NOT_REGISTERED);
        }

        // 부분 업데이트라 요청에 없는 필드는 기존 값을 같이 고려해서 검증

        boolean imageOrGenderChanged =
                request.imageUrl() != null || request.gender() != null;

        if (imageOrGenderChanged) {
            Gender genderToValidate = request.gender() != null ? request.gender() : member.getGender();
            String imageUrlToValidate = request.imageUrl() != null ? request.imageUrl() : member.getImageUrl();

            if (genderToValidate != null && imageUrlToValidate != null
                    && !presetImageRepository.existsByGenderAndImageUrl(genderToValidate, imageUrlToValidate)) {
                throw new BaseException(MemberErrorCode.INVALID_PRESET_IMAGE);
            }
        }
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
