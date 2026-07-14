package com.example.umcCall.domain.ai.exception;

import com.example.umcCall.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AiErrorCode implements BaseErrorCode {

    AI_SERVER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AI503_1", "AI 서버에 연결할 수 없습니다."),
    AI_SERVER_ERROR(HttpStatus.BAD_GATEWAY, "AI502_1", "AI 서버가 요청 처리에 실패했습니다."),
    EMPTY_AI_RESPONSE(HttpStatus.BAD_GATEWAY, "AI502_2", "AI 서버가 빈 응답을 반환했습니다."),
    STALE_RELATIONSHIP(HttpStatus.CONFLICT, "AI409_1", "AI 요청 중 관계 정보가 변경되어 응답을 폐기했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
