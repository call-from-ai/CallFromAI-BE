package com.example.umcCall.domain.chat.exception;

import com.example.umcCall.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 채팅 도메인 에러 코드.
 */
@Getter
@RequiredArgsConstructor
public enum ChatErrorCode implements BaseErrorCode {

    CHATROOM_RELATIONSHIP_ID_REQUIRED(HttpStatus.BAD_REQUEST, "CHAT400_1", "CHARACTER 타입 채팅방은 관계 ID가 필요합니다."),
    CHATROOM_RELATIONSHIP_ACCESS_DENIED(HttpStatus.FORBIDDEN, "CHAT403_1", "본인의 관계에 대해서만 채팅방을 생성할 수 있습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
