package com.example.umcCall.domain.auth.service;

import com.example.umcCall.domain.auth.client.KakaoApiClient;
import com.example.umcCall.domain.auth.dto.response.KakaoUserResponse;
import com.example.umcCall.domain.auth.dto.response.TokenResponse;
import com.example.umcCall.domain.auth.exception.AuthErrorCode;
import com.example.umcCall.domain.auth.repository.RefreshTokenRepository;
import com.example.umcCall.domain.member.entity.Member;
import com.example.umcCall.domain.member.enums.SocialType;
import com.example.umcCall.domain.member.exception.MemberErrorCode;
import com.example.umcCall.domain.member.repository.MemberRepository;
import com.example.umcCall.domain.term.service.TermService;
import com.example.umcCall.global.exception.BaseException;
import com.example.umcCall.global.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final KakaoApiClient kakaoApiClient;
    private final MemberRepository memberRepository;
    private final JwtProvider jwtProvider;
    private final TermService termService;

    @Transactional
    public TokenResponse kakaoLogin(String kakaoAccessToken) {
        KakaoUserResponse userInfo = kakaoApiClient.getUserInfo(kakaoAccessToken);
        String socialUid = String.valueOf(userInfo.id());

        Member member = memberRepository.findBySocialUidAndSocialType(socialUid, SocialType.KAKAO)
                .orElseGet(() -> memberRepository.save(
                        Member.createBySocialLogin(socialUid, SocialType.KAKAO)
                ));

        return issueTokens(member);
    }

    @Transactional
    public TokenResponse reissueToken(String refreshToken) {
        jwtProvider.validateRefreshToken(refreshToken);
        Long memberId = jwtProvider.getMemberId(refreshToken);

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BaseException(MemberErrorCode.MEMBER_NOT_FOUND));

        if (member.getRefreshToken() == null || !member.getRefreshToken().equals(refreshToken)) {
            throw new BaseException(AuthErrorCode.INVALID_TOKEN);
        }

        return issueTokens(member);
    }

    @Transactional
    public TokenResponse testLogin(String socialUid) {
        Member member = memberRepository.findBySocialUidAndSocialType(socialUid, SocialType.KAKAO)
                .orElseGet(() -> memberRepository.save(
                        Member.createBySocialLogin(socialUid, SocialType.KAKAO)
                ));

        return issueTokens(member);
    }

    @Transactional
    public void logout(Long memberId) {
        memberRepository.findById(memberId)
                .ifPresent(member -> member.updateRefreshToken(null));
    }

    private TokenResponse issueTokens(Member member) {
        Long memberId = member.getId();
        String accessToken = jwtProvider.createAccessToken(memberId);
        String refreshToken = jwtProvider.createRefreshToken(memberId);

        member.updateRefreshToken(refreshToken);

        boolean needsOnboarding = !member.isOnboardingCompleted();
        boolean needsTermsAgreement = !termService.hasAgreedAllRequiredTerms(memberId);

        return new TokenResponse(accessToken, refreshToken, needsOnboarding, needsTermsAgreement);
    }
}