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

    // 400
    CALL_TARGET_NOT_MAIN(HttpStatus.BAD_REQUEST, "CALL400_1", "메인(활성) 캐릭터에게만 통화할 수 있습니다."),
    CALL_RESERVATION_PAST_TIME(HttpStatus.BAD_REQUEST, "CALL400_2", "지난 시각으로는 통화를 예약할 수 없습니다."),

    // 403
    CALL_TARGET_ACCESS_DENIED(HttpStatus.FORBIDDEN, "CALL403_1", "본인의 캐릭터에게만 통화할 수 있습니다."),
    CALL_ACCESS_DENIED(HttpStatus.FORBIDDEN, "CALL403_2", "본인의 통화만 이용할 수 있습니다."),
    CALL_RESERVATION_ACCESS_DENIED(HttpStatus.FORBIDDEN, "CALL403_3", "본인의 통화 예약만 이용할 수 있습니다."),

    // 404
    CALL_TARGET_NOT_FOUND(HttpStatus.NOT_FOUND, "CALL404_1", "통화할 캐릭터를 찾을 수 없습니다."),
    CALL_NOT_FOUND(HttpStatus.NOT_FOUND, "CALL404_2", "통화를 찾을 수 없습니다."),
    CALL_RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "CALL404_3", "통화 예약을 찾을 수 없습니다."),

    // 409
    CALL_NOT_COMPLETED(HttpStatus.CONFLICT, "CALL409_1", "완료된 통화만 조회할 수 있습니다."),
    CALL_NOT_RINGING(HttpStatus.CONFLICT, "CALL409_2", "착신 대기 중인 통화만 받을 수 있습니다."),
    CALL_RESERVATION_NOT_SCHEDULED(HttpStatus.CONFLICT, "CALL409_3", "대기 중인 통화 예약만 수정할 수 있습니다."),
    CALL_NOT_IN_PROGRESS(HttpStatus.CONFLICT, "CALL409_4", "진행 중인 통화만 종료할 수 있습니다."),
    CALL_RESERVATION_ALREADY_EXISTS(HttpStatus.CONFLICT, "CALL409_5", "이미 대기 중인 통화 예약이 있습니다."),
    CALL_ALREADY_ACTIVE(HttpStatus.CONFLICT, "CALL409_6", "이미 진행 중인 통화가 있습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
