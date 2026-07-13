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

    // Auth
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "AUTH401_1", "인증이 필요합니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH401_2", "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH401_3", "만료된 토큰입니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "AUTH403_1", "접근 권한이 없습니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH401_4", "아이디 또는 비밀번호가 올바르지 않습니다."),
    TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "AUTH401_5", "헤더에 토큰이 존재하지 않습니다."),

    // Member
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "MEMBER404_1", "존재하지 않는 사용자입니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "MEMBER409_1", "이미 사용중인 이메일입니다."),
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "MEMBER409_2", "이미 사용중인 닉네임입니다."),

    // External Service / S3 / API
    FILE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "EXTERNAL500_1", "S3 파일 업로드에 실패했습니다."),
    FILE_SIZE_EXCEEDED(HttpStatus.BAD_REQUEST, "EXTERNAL400_1", "파일 업로드 용량을 초과했습니다."),
    INVALID_FILE_EXTENSION(HttpStatus.BAD_REQUEST, "EXTERNAL400_2", "허용되지 않는 파일 확장자입니다."),
    EXTERNAL_API_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "EXTERNAL500_2", "외부 연동 API 호출에 실패했습니다."),

    // Term
    REQUIRED_TERM_NOT_AGREED(HttpStatus.BAD_REQUEST, "TERM400_1", "필수 약관에 동의해야 합니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
