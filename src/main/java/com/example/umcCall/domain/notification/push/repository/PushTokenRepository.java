package com.example.umcCall.domain.notification.push.repository;

import com.example.umcCall.domain.notification.push.entity.PushToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PushTokenRepository extends JpaRepository<PushToken, Long> {

    Optional<PushToken> findByToken(String token);

    /** 로그아웃 시 본인 소유 토큰만 지운다(남의 토큰 임의 삭제 방지). */
    void deleteByTokenAndMemberId(String token, Long memberId);
}
