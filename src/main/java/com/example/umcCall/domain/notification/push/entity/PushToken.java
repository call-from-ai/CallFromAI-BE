package com.example.umcCall.domain.notification.push.entity;

import com.example.umcCall.domain.notification.push.enums.PushPlatform;
import com.example.umcCall.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * FCM 디바이스 푸시 토큰. 기기(앱 설치) 1개당 1개로 전역 유일하므로 token에 유니크 제약을 둔다.
 * 한 회원이 여러 기기를 쓸 수 있어 member_id는 중복 가능하지만, 하나의 token은 항상 한 회원에게만 매인다.
 */
@Entity
@Getter
@Table(
        name = "push_token",
        uniqueConstraints = @UniqueConstraint(name = "uk_push_token_token", columnNames = "token"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushToken extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "push_token_id")
    private Long id;

    /** 사용자 FK */
    @Column(name = "member_id", nullable = false)
    private Long memberId;

    /** FCM 기기 토큰. 전역 유일. */
    @Column(name = "token", nullable = false, length = 512)
    private String token;

    /** 토큰이 발급된 기기 플랫폼(기록/디버깅용). */
    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false)
    private PushPlatform platform;

    @Builder
    private PushToken(Long memberId, String token, PushPlatform platform) {
        this.memberId = memberId;
        this.token = token;
        this.platform = platform;
    }

    /** 같은 토큰이 재등록될 때 소유 회원/플랫폼을 갱신한다(기기 인수인계 대비). */
    public void reassign(Long memberId, PushPlatform platform) {
        this.memberId = memberId;
        this.platform = platform;
    }
}
