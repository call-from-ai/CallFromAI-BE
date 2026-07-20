package com.example.umcCall.domain.call.exception;

import com.example.umcCall.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 통화 도메인 에러 코드. 코드 형식: CALL{HTTP상태}_{일련번호}.
 * 실제로 쓰이는 코드만 추가하고, 새 기능이 생기면 그때 확장한다.
 */
@Getter
@RequiredArgsConstructor
public enum CallErrorCode implements BaseErrorCode {

    // 400 - 메인(활성) 캐릭터에게만 통화할 수 있다.
    CALL_TARGET_NOT_MAIN(HttpStatus.BAD_REQUEST, "CALL400_1", "메인(활성) 캐릭터에게만 통화할 수 있습니다."),

    // 403 - 본인 소유가 아닌 캐릭터로 통화 시도
    CALL_TARGET_ACCESS_DENIED(HttpStatus.FORBIDDEN, "CALL403_1", "본인의 캐릭터에게만 통화할 수 있습니다."),

    // 404 - 통화 대상 캐릭터(관계)를 찾을 수 없음
    CALL_TARGET_NOT_FOUND(HttpStatus.NOT_FOUND, "CALL404_1", "통화할 캐릭터를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
