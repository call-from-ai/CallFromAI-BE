package com.example.umcCall.domain.notification.push.repository;

import com.example.umcCall.domain.notification.push.entity.PushToken;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface PushTokenRepository extends JpaRepository<PushToken, Long> {

    Optional<PushToken> findByToken(String token);

    /** 발송 대상: 한 회원의 모든 기기 토큰. */
    List<PushToken> findByMemberId(Long memberId);

    /** 로그아웃 시 본인 소유 토큰만 지운다(남의 토큰 임의 삭제 방지). 자체 트랜잭션으로 실행된다. */
    @Transactional
    void deleteByTokenAndMemberId(String token, Long memberId);

    /** 발송 결과 무효로 판정된 죽은 토큰들을 일괄 정리한다. 발송(네트워크) 밖에서 짧은 자체 트랜잭션으로 실행된다. */
    @Transactional
    void deleteByTokenIn(Collection<String> tokens);

    /** 회원 탈퇴 시 토큰 삭제 **/
    void deleteByMemberId(Long memberId);
}
