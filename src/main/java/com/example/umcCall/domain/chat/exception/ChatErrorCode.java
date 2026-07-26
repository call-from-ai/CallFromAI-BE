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

    // 400
    CHATROOM_RELATIONSHIP_ID_REQUIRED(HttpStatus.BAD_REQUEST, "CHAT400_1", "CHARACTER 타입 채팅방은 관계 ID가 필요합니다."),
    EMPTY_MESSAGE(HttpStatus.BAD_REQUEST, "CHAT400_2", "내용이 없는 메시지는 보낼 수 없습니다."),
    INVALID_IMAGE_TYPE(HttpStatus.BAD_REQUEST, "CHAT400_3", "지원하지 않는 이미지 형식입니다. (JPEG, PNG만 가능)"),

    // 403
    CHATROOM_RELATIONSHIP_ACCESS_DENIED(HttpStatus.FORBIDDEN, "CHAT403_1", "본인의 관계에 대해서만 채팅방을 생성할 수 있습니다."),
    CHATROOM_ACCESS_DENIED(HttpStatus.FORBIDDEN, "CHAT403_2", "접근 권한이 없는 채팅방입니다."),

    // 404
    CHATROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "CHAT404_1", "채팅방을 찾을 수 없습니다."),
    CHATROOM_HIDDEN(HttpStatus.NOT_FOUND, "CHAT404_2", "이미 목록에서 숨긴 채팅방입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
