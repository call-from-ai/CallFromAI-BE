package com.example.umcCall.global.security;

import com.example.umcCall.domain.auth.exception.AuthErrorCode;
import com.example.umcCall.global.exception.BaseException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtProvider {

    private static final String TOKEN_TYPE_CLAIM = "tokenType";
    private static final String ACCESS_TOKEN_TYPE = "ACCESS";
    private static final String REFRESH_TOKEN_TYPE = "REFRESH";

    private final SecretKey key;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;

    public JwtProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration}") long accessTokenExpiration,
            @Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration
    ) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
    }

    public String createAccessToken(Long memberId) {
        return createToken(memberId, accessTokenExpiration, ACCESS_TOKEN_TYPE);
    }

    public String createRefreshToken(Long memberId) {
        return createToken(memberId, refreshTokenExpiration, REFRESH_TOKEN_TYPE);
    }

    private String createToken(Long memberId, long expiration, String tokenType) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(memberId))
                .claim(TOKEN_TYPE_CLAIM, tokenType)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiration))
                .signWith(key)
                .compact();
    }

    public Long getMemberId(String token) {
        return Long.valueOf(parseClaims(token).getSubject());
    }

    public void validateAccessToken(String token) {
        validateTokenType(token, ACCESS_TOKEN_TYPE);
    }

    public void validateRefreshToken(String token) {
        validateTokenType(token, REFRESH_TOKEN_TYPE);
    }

    private void validateTokenType(String token, String expectedType) {
        Claims claims = parseClaims(token);
        String actualType = claims.get(TOKEN_TYPE_CLAIM, String.class);

        if (!expectedType.equals(actualType)) {
            throw new BaseException(AuthErrorCode.INVALID_TOKEN);
        }
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new BaseException(AuthErrorCode.EXPIRED_TOKEN);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BaseException(AuthErrorCode.INVALID_TOKEN);
        }
    }
}