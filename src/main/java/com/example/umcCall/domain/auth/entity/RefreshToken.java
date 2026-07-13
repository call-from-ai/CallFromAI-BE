package com.example.umcCall.domain.auth.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "refresh_token")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    @Id
    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "token", nullable = false, length = 500)
    private String token;

    public RefreshToken(Long memberId, String token) {
        this.memberId = memberId;
        this.token = token;
    }

    public void updateToken(String token) {
        this.token = token;
    }

    public String getToken() {
        return token;
    }

    public Long getMemberId() {
        return memberId;
    }
}