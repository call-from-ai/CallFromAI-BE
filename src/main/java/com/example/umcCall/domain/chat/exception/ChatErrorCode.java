package com.example.umcCall.domain.chat.exception;

import com.example.umcCall.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 채팅 도메인 에러 코드. 코드 형식: CHAT{HTTP상태}_{일련번호}.
 * 실제로 쓰이는 코드만 추가하고, 새 기능이 생기면 그때 확장한다.
 */
@Getter
@RequiredArgsConstructor
public enum ChatErrorCode implements BaseErrorCode {

    ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "CHAT404_1", "채팅방을 찾을 수 없습니다."),
    ROOM_HIDDEN(HttpStatus.NOT_FOUND, "CHAT404_2", "이미 목록에서 숨긴 채팅방입니다."),
    ROOM_ACCESS_DENIED(HttpStatus.FORBIDDEN, "CHAT403_1", "접근 권한이 없는 채팅방입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
