package com.example.umcCall.global.error.exception;

import com.example.umcCall.global.error.ErrorCode;
import lombok.Getter;

/**
 * 서비스 로직에서 의도적으로 던지는 예외의 최상위 클래스.
 */
@Getter
public class BaseException extends RuntimeException {

    private final ErrorCode errorCode;

    public BaseException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BaseException(ErrorCode errorCode, String customMessage) {
        super(customMessage);
        this.errorCode = errorCode;
    }
}
