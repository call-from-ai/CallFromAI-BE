package com.example.umcCall.global.exception;

import com.example.umcCall.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;

/**
 * 서비스 로직에서 의도적으로 던지는 예외의 최상위 클래스.
 * 도메인별 커스텀 예외(XxxException)는 이 클래스를 상속한다.
 */
@Getter
public class BaseException extends RuntimeException {

    private final BaseErrorCode errorCode;

    public BaseException(BaseErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BaseException(BaseErrorCode errorCode, String customMessage) {
        super(customMessage);
        this.errorCode = errorCode;
    }
}
