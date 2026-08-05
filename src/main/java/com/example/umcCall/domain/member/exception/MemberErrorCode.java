package com.example.umcCall.domain.member.exception;

import com.example.umcCall.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MemberErrorCode implements BaseErrorCode {

    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "MEMBER404_1", "존재하지 않는 사용자입니다."),
    INVALID_PRESET_IMAGE(HttpStatus.BAD_REQUEST, "MEMBER400_2", "존재하지 않는 프리셋 이미지입니다."),
    MEMBER_INFO_ALREADY_REGISTERED(HttpStatus.CONFLICT, "MEMBER409_1", "회원 정보가 이미 존재합니다."),
    MEMBER_INFO_NOT_REGISTERED(HttpStatus.CONFLICT, "MEMBER409_2", "회원 정보를 먼저 입력해야 합니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
