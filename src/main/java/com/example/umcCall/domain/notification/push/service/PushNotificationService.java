package com.example.umcCall.domain.notification.push.service;

import com.example.umcCall.domain.member.entity.Member;
import com.example.umcCall.domain.member.repository.MemberRepository;
import com.example.umcCall.domain.notification.push.dto.PushMessage;
import com.example.umcCall.domain.notification.push.entity.PushToken;
import com.example.umcCall.domain.notification.push.repository.PushTokenRepository;
import com.example.umcCall.global.infra.fcm.FcmSender;
import java.time.LocalTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 공용 푸시 발송기. 채팅·통화·알림 도메인이 memberId + PushMessage로 호출하면
 * 회원 알림설정을 확인하고, 그 회원의 모든 기기에 FCM으로 보낸 뒤 무효 토큰을 정리한다.
 * 발송 판정 중 방 단위(음소거)는 호출자가 판단하며, 이 서비스는 회원 단위 설정만 본다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushNotificationService {

    private final PushTokenRepository pushTokenRepository;
    private final MemberRepository memberRepository;   // 회원 알림설정 읽기 전용
    private final FcmSender fcmSender;

    public void send(Long memberId, PushMessage message) {
        Member member = memberRepository.findById(memberId).orElse(null);
        if (member == null || !isAllowedByMemberSetting(member)) {
            return;   // 회원 없음 / 전체 알림 OFF / 방해금지 시간대 → 발송 스킵
        }

        List<PushToken> tokens = pushTokenRepository.findByMemberId(memberId);
        if (tokens.isEmpty()) {
            return;   // 등록된 기기 없음
        }
        List<String> tokenStrings = tokens.stream().map(PushToken::getToken).toList();

        List<String> invalidTokens = fcmSender.send(tokenStrings, message);
        if (!invalidTokens.isEmpty()) {
            pushTokenRepository.deleteByTokenIn(invalidTokens);   // 죽은 토큰 정리
        }
    }

    /**
     * 회원 단위 발송 허용 판정.
     * - 전체 알림 OFF면 모두 차단.
     * - 방해금지 시간대(회원이 설정한 start~end, 자정 넘김 지원)면 차단.
     * (심야 통화 nightCallAllowed는 CALL 종류에만 해당하고 '심야' 범위 합의가 필요해, 통화 연동 단계에서 확인해주세여)
     */
    private boolean isAllowedByMemberSetting(Member member) {
        if (!member.isAllNotificationEnabled()) {
            return false;
        }
        return !isInDoNotDisturb(member, LocalTime.now());
    }

    private boolean isInDoNotDisturb(Member member, LocalTime now) {
        LocalTime start = member.getDoNotDisturbStart();
        LocalTime end = member.getDoNotDisturbEnd();
        if (start == null || end == null || start.equals(end)) {
            return false;   // 방해금지 미설정
        }
        if (start.isBefore(end)) {
            return !now.isBefore(start) && now.isBefore(end);       // 같은 날 구간 [start, end)
        }
        return !now.isBefore(start) || now.isBefore(end);           // 자정을 넘는 구간(예: 22:00~08:00)
    }
}
