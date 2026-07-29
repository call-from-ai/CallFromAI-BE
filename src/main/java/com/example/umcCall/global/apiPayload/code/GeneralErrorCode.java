package com.example.umcCall.global.apiPayload.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 전역 공통 에러 코드.
 * 도메인별 에러는 각 도메인이 {@link BaseErrorCode}를 구현한 자신의 enum으로 관리한다.
 * (Auth/Member/External 항목은 담당 도메인 enum이 생기면 그쪽으로 이관 예정)
 *
 * 코드 형식: {도메인}{HTTP상태}_{일련번호}
 */
@Getter
@RequiredArgsConstructor
public enum GeneralErrorCode implements BaseErrorCode {

    // Common
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "COMMON400_1", "요청 값이 올바르지 않습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "COMMON405_1", "허용되지 않은 HTTP 메서드입니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON404_1", "존재하지 않는 엔드포인트(URL)입니다."),
    ENTITY_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON404_2", "요청한 리소스를 찾을 수 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON500_1", "서버 내부 오류가 발생했습니다."),
    INVALID_TYPE_VALUE(HttpStatus.BAD_REQUEST, "COMMON400_2", "요청 파라미터의 타입이 올바르지 않습니다."),
    MISSING_REQUEST_PARAMETER(HttpStatus.BAD_REQUEST, "COMMON400_3", "필수 요청 파라미터가 누락되었습니다."),
    INVALID_HTTP_BODY(HttpStatus.BAD_REQUEST, "COMMON400_4", "HTTP 요청 바디(JSON) 파싱에 실패했습니다."),
    MULTIPART_FILE_ERROR(HttpStatus.BAD_REQUEST, "COMMON400_5", "파일 업로드 처리 중 오류가 발생했습니다."),
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "COMMON409_1", "이미 존재하는 리소스입니다."),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "COMMON409_2", "동시에 변경된 리소스입니다. 잠시 후 다시 시도해 주세요."),
    DATA_INTEGRITY_CONFLICT(HttpStatus.CONFLICT, "COMMON409_3", "데이터 제약 조건과 충돌했습니다."),

    // External Service / S3 / API
    FILE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "EXTERNAL500_1", "S3 파일 업로드에 실패했습니다."),
    FILE_SIZE_EXCEEDED(HttpStatus.BAD_REQUEST, "EXTERNAL400_1", "파일 업로드 용량을 초과했습니다."),
    INVALID_FILE_EXTENSION(HttpStatus.BAD_REQUEST, "EXTERNAL400_2", "허용되지 않는 파일 확장자입니다."),
    EXTERNAL_API_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "EXTERNAL500_2", "외부 연동 API 호출에 실패했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
