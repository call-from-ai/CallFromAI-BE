package com.example.umcCall.domain.ai.exception;

import com.example.umcCall.global.exception.BaseException;

public class AiServerException extends BaseException {

    public AiServerException(AiErrorCode errorCode) {
        super(errorCode);
    }

    public AiServerException(AiErrorCode errorCode, Throwable cause) {
        super(errorCode);
        initCause(cause);
    }
}
