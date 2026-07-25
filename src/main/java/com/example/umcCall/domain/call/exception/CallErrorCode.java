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

    // 400 - 지난 시각으로 예약 생성 시도(스케줄러가 곧 종결시킬 예약이라 받지 않는다)
    CALL_RESERVATION_PAST_TIME(HttpStatus.BAD_REQUEST, "CALL400_2", "지난 시각으로는 통화를 예약할 수 없습니다."),

    // 403 - 본인 소유가 아닌 캐릭터로 통화 시도
    CALL_TARGET_ACCESS_DENIED(HttpStatus.FORBIDDEN, "CALL403_1", "본인의 캐릭터에게만 통화할 수 있습니다."),

    // 403 - 본인 소유가 아닌 통화에 접근(기록/전문 조회, 착신 수락 등). 통화 단위 소유 검증이 공유한다.
    CALL_ACCESS_DENIED(HttpStatus.FORBIDDEN, "CALL403_2", "본인의 통화만 이용할 수 있습니다."),

    // 404 - 통화 대상 캐릭터(관계)를 찾을 수 없음
    CALL_TARGET_NOT_FOUND(HttpStatus.NOT_FOUND, "CALL404_1", "통화할 캐릭터를 찾을 수 없습니다."),

    // 404 - callId로 통화 기록을 찾을 수 없음(상태 전이 대상 부재)
    CALL_NOT_FOUND(HttpStatus.NOT_FOUND, "CALL404_2", "통화를 찾을 수 없습니다."),

    // 409 - 완료되지 않은 통화의 기록(상세/전문) 조회 시도. 상세·전문 조회가 공유한다.
    CALL_NOT_COMPLETED(HttpStatus.CONFLICT, "CALL409_1", "완료된 통화만 조회할 수 있습니다."),

    // 409 - 착신 대기(RINGING)가 아닌 통화를 받으려는 시도
    CALL_NOT_RINGING(HttpStatus.CONFLICT, "CALL409_2", "착신 대기 중인 통화만 받을 수 있습니다."),

    // 403 - 본인 소유가 아닌 통화 예약에 접근
    CALL_RESERVATION_ACCESS_DENIED(HttpStatus.FORBIDDEN, "CALL403_3", "본인의 통화 예약만 이용할 수 있습니다."),

    // 404 - reservationId로 통화 예약을 찾을 수 없음
    CALL_RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "CALL404_3", "통화 예약을 찾을 수 없습니다."),

    // 409 - 이미 발신됐거나 취소된 예약을 수정하려는 시도
    CALL_RESERVATION_NOT_SCHEDULED(HttpStatus.CONFLICT, "CALL409_3", "대기 중인 통화 예약만 수정할 수 있습니다."),

    // 409 - 진행 중이 아닌 통화를 종료하려는 시도
    CALL_NOT_IN_PROGRESS(HttpStatus.CONFLICT, "CALL409_4", "진행 중인 통화만 종료할 수 있습니다."),

    // 409 - 같은 관계에 대기 중인 예약이 이미 있는데 또 예약하려는 시도(관계당 1건)
    CALL_RESERVATION_ALREADY_EXISTS(HttpStatus.CONFLICT, "CALL409_5", "이미 대기 중인 통화 예약이 있습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
