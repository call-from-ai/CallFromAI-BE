package com.example.umcCall.global.apiPayload.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 전역 공통 성공 코드.
 * 도메인별 성공 코드는 각 도메인이 {@link BaseSuccessCode}를 구현한 자신의 enum으로 관리한다.
 *
 * 코드 형식: {도메인}{HTTP상태}_{일련번호}
 */
@Getter
@RequiredArgsConstructor
public enum GeneralSuccessCode implements BaseSuccessCode {

    OK(HttpStatus.OK, "COMMON200_1", "성공적으로 요청을 처리했습니다."),
    CREATED(HttpStatus.CREATED, "COMMON201_1", "성공적으로 생성했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
