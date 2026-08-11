package com.example.umcCall.domain.member.service;

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
import com.example.umcCall.domain.notification.push.repository.PushTokenRepository;
import com.example.umcCall.domain.notification.repository.ActivityNotificationRepository;
import com.example.umcCall.domain.term.repository.MemberTermRepository;
import com.example.umcCall.global.apiPayload.code.GeneralErrorCode;
import com.example.umcCall.global.exception.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final CharacterService characterService;
    private final PresetImageRepository presetImageRepository;
    private final ActivityNotificationRepository activityNotificationRepository;
    private final PushTokenRepository pushTokenRepository;
    private final MemberTermRepository memberTermRepository;

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

        // imageUrl은 String이라 ""가 와도 Jackson이 null로 바꿔주지 않는다.
        // 여기서 직접 blank를 null로 취급해야 "사진 선택 안 함"과 "빈 문자열"이 같게 처리된다.
        String imageUrl = (request.imageUrl() == null || request.imageUrl().isBlank()) ? null : request.imageUrl();

        if (request.gender() != null && imageUrl != null
                && !presetImageRepository.existsByGenderAndImageUrl(request.gender(), imageUrl)) {
            throw new BaseException(MemberErrorCode.INVALID_PRESET_IMAGE);
        }


        member.updateProfile(
                request.lastName(), request.firstName(), imageUrl,
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
        String requestImageUrl = (request.imageUrl() == null || request.imageUrl().isBlank())
                ? null : request.imageUrl();

        boolean imageOrGenderChanged =
                requestImageUrl != null || request.gender() != null;

        if (imageOrGenderChanged) {
            Gender genderToValidate = request.gender() != null ? request.gender() : member.getGender();
            String imageUrlToValidate = requestImageUrl != null ? requestImageUrl : member.getImageUrl();

            if (genderToValidate != null && imageUrlToValidate != null
                    && !presetImageRepository.existsByGenderAndImageUrl(genderToValidate, imageUrlToValidate)) {
                throw new BaseException(MemberErrorCode.INVALID_PRESET_IMAGE);
            }
        }
        member.updateProfile(
                request.lastName(), request.firstName(), requestImageUrl,
                request.gender(), request.birth(), request.mbti(), request.job()
        );
        return MemberResponse.from(member);
    }

    @Transactional
    public void withdraw(Long memberId) {
        Member member = findMember(memberId);
        characterService.deleteAllCharactersForWithdraw(memberId);
        // 회원 관련 데이터는 AI 서버와 무관하니 즉시 정리
        memberTermRepository.deleteByMemberId(memberId);
        activityNotificationRepository.deleteByMemberId(memberId);
        pushTokenRepository.deleteByMemberId(memberId);

        // 회원 row 자체도 즉시 하드 삭제
        memberRepository.delete(member);
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

    @Transactional
    public NotificationSettingResponse deleteDoNotDisturb(Long memberId) {
        Member member = findMember(memberId);
        member.updateDoNotDisturb(null, null);
        return NotificationSettingResponse.from(member);
    }
}
