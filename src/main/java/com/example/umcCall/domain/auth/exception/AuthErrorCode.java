package com.example.umcCall.domain.auth.exception;

import com.example.umcCall.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements BaseErrorCode {

    INVALID_KAKAO_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH401_1", "유효하지 않거나 만료된 카카오 액세스 토큰입니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH401_2", "유효하지 않은 엑세스 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH401_3", "만료된 엑세스 토큰입니다."),
    TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "AUTH401_4", "헤더에 토큰이 존재하지 않습니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "AUTH403_1", "접근 권한이 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}