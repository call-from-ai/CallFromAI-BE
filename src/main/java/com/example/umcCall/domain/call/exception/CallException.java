package com.example.umcCall.domain.call.exception;

import com.example.umcCall.global.apiPayload.code.BaseErrorCode;
import com.example.umcCall.global.exception.BaseException;

/**
 * 통화 도메인 비즈니스 예외. GlobalExceptionAdvice가 부모(BaseException)로 받아 처리한다.
 */
public class CallException extends BaseException {

    public CallException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}
