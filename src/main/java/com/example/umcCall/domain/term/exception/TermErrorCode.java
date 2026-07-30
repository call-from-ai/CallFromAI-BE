package com.example.umcCall.domain.term.exception;

import com.example.umcCall.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TermErrorCode implements BaseErrorCode {

    REQUIRED_TERM_NOT_AGREED(HttpStatus.BAD_REQUEST, "TERM400_1", "필수 약관에 동의해야 합니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
