package com.example.umcCall.domain.auth.service;

import com.example.umcCall.domain.auth.client.KakaoApiClient;
import com.example.umcCall.domain.auth.dto.response.KakaoUserResponse;
import com.example.umcCall.domain.auth.dto.response.TokenResponse;
import com.example.umcCall.domain.auth.entity.RefreshToken;
import com.example.umcCall.domain.auth.repository.RefreshTokenRepository;
import com.example.umcCall.domain.member.entity.Member;
import com.example.umcCall.domain.member.enums.SocialType;
import com.example.umcCall.domain.member.repository.MemberRepository;
import com.example.umcCall.global.apiPayload.code.GeneralErrorCode;
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
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public TokenResponse kakaoLogin(String kakaoAccessToken) {
        KakaoUserResponse userInfo = kakaoApiClient.getUserInfo(kakaoAccessToken);
        String socialUid = String.valueOf(userInfo.id());

        Member member = memberRepository.findBySocialUidAndSocialType(socialUid, SocialType.KAKAO)
                .orElseGet(() -> memberRepository.save(
                        Member.createBySocialLogin(socialUid, SocialType.KAKAO)
                ));

        return issueTokens(member.getId(), !member.isOnboardingCompleted());
    }

    @Transactional
    public TokenResponse reissueToken(String refreshToken) {
        jwtProvider.validateRefreshToken(refreshToken);
        Long memberId = jwtProvider.getMemberId(refreshToken);

        RefreshToken saved = refreshTokenRepository.findByMemberId(memberId)
                .orElseThrow(() -> new BaseException(GeneralErrorCode.INVALID_TOKEN));

        if (!saved.getToken().equals(refreshToken)) {
            throw new BaseException(GeneralErrorCode.INVALID_TOKEN, "이미 사용되었거나 유효하지 않은 리프레시 토큰입니다.");
        }

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BaseException(GeneralErrorCode.MEMBER_NOT_FOUND));


        // 탈퇴한 회원은 재발급 못 받게 하기
        if (member.getIsInactive()) {
            throw new BaseException(GeneralErrorCode.INVALID_TOKEN, "탈퇴한 회원입니다.");
        }

        return issueTokens(memberId, !member.isOnboardingCompleted());
    }

    @Transactional
    public TokenResponse testLogin(String socialUid) {
        Member member = memberRepository.findBySocialUidAndSocialType(socialUid, SocialType.KAKAO)
                .orElseGet(() -> memberRepository.save(
                        Member.createBySocialLogin(socialUid, SocialType.KAKAO)
                ));

        return issueTokens(member.getId(), !member.isOnboardingCompleted());
    }

    @Transactional
    public void logout(Long memberId) {
        refreshTokenRepository.findByMemberId(memberId)
                .ifPresent(refreshTokenRepository::delete);
    }

    private TokenResponse issueTokens(Long memberId, boolean needsOnboarding) {
        String accessToken = jwtProvider.createAccessToken(memberId);
        String refreshToken = jwtProvider.createRefreshToken(memberId);

        RefreshToken tokenEntity = refreshTokenRepository.findByMemberId(memberId)
                .orElse(new RefreshToken(memberId, refreshToken));
        tokenEntity.updateToken(refreshToken);
        refreshTokenRepository.save(tokenEntity);

        return new TokenResponse(accessToken, refreshToken, needsOnboarding);
    }
}