package com.example.umcCall.domain.auth.service;

import com.example.umcCall.domain.auth.client.KakaoApiClient;
import com.example.umcCall.domain.auth.dto.response.KakaoUserResponse;
import com.example.umcCall.domain.auth.dto.response.TokenResponse;
import com.example.umcCall.domain.member.entity.Member;
import com.example.umcCall.domain.member.enums.SocialType;
import com.example.umcCall.domain.member.repository.MemberRepository;
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

    @Transactional
    public TokenResponse kakaoLogin(String kakaoAccessToken) {
        KakaoUserResponse userInfo = kakaoApiClient.getUserInfo(kakaoAccessToken);
        String socialUid = String.valueOf(userInfo.id());

        Member member = memberRepository.findBySocialUidAndSocialType(socialUid, SocialType.KAKAO)
                .orElseGet(() -> memberRepository.save(
                        Member.createBySocialLogin(socialUid, SocialType.KAKAO)
                ));

        String accessToken = jwtProvider.createAccessToken(member.getId());
        String refreshToken = jwtProvider.createRefreshToken(member.getId());

        return new TokenResponse(accessToken, refreshToken, !member.isOnboardingCompleted());
    }

    public TokenResponse reissueToken(String refreshToken) {
        jwtProvider.validateRefreshToken(refreshToken);
        Long memberId = jwtProvider.getMemberId(refreshToken);

        String newAccessToken = jwtProvider.createAccessToken(memberId);
        String newRefreshToken = jwtProvider.createRefreshToken(memberId);

        return new TokenResponse(newAccessToken, newRefreshToken, false);
    }

    @Transactional
    public TokenResponse testLogin(String socialUid) {
        Member member = memberRepository.findBySocialUidAndSocialType(socialUid, SocialType.KAKAO)
                .orElseGet(() -> memberRepository.save(
                        Member.createBySocialLogin(socialUid, SocialType.KAKAO)
                ));

        String accessToken = jwtProvider.createAccessToken(member.getId());
        String refreshToken = jwtProvider.createRefreshToken(member.getId());

        return new TokenResponse(accessToken, refreshToken, !member.isOnboardingCompleted());
    }
}