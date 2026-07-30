package com.example.umcCall.domain.notification.push.service;

import com.example.umcCall.domain.notification.push.dto.request.PushTokenRegisterRequest;
import com.example.umcCall.domain.notification.push.entity.PushToken;
import com.example.umcCall.domain.notification.push.repository.PushTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 푸시 토큰 등록/삭제. 실제 발송(발송 판정 + FCM 전송)은 다음 단계에서 별도 처리한다.
 */
@Service
@RequiredArgsConstructor
public class PushTokenService {

    private final PushTokenRepository pushTokenRepository;

    /**
     * 토큰 등록(upsert). 같은 토큰이 이미 있으면 소유 회원/플랫폼만 갱신하고, 없으면 새로 저장한다.
     * 앱이 시작마다 보내도 안전하도록 멱등하게 처리한다.
     */
    @Transactional
    public void register(Long memberId, PushTokenRegisterRequest request) {
        pushTokenRepository.findByToken(request.token())
                .ifPresentOrElse(
                        existing -> existing.reassign(memberId, request.platform()),
                        () -> pushTokenRepository.save(PushToken.builder()
                                .memberId(memberId)
                                .token(request.token())
                                .platform(request.platform())
                                .build()));
    }

    /**
     * 토큰 삭제(로그아웃). 본인 소유 토큰 1건만 제거하며, 없는 토큰이어도 조용히 성공한다(멱등).
     */
    @Transactional
    public void delete(Long memberId, String token) {
        pushTokenRepository.deleteByTokenAndMemberId(token, memberId);
    }
}
