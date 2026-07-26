package com.example.umcCall.global.apiPayload.code;

import org.springframework.http.HttpStatus;

/**
 * 모든 에러 코드 enum이 구현하는 공통 인터페이스.
 * ApiResponse는 이 인터페이스에만 의존하므로 어떤 도메인 코드든 받을 수 있다.
 */
public interface BaseErrorCode {

    HttpStatus getStatus();

    String getCode();

    String getMessage();
}
