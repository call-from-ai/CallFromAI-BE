package com.example.umcCall.domain.relationship.exception;

import com.example.umcCall.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum RelationshipErrorCode implements BaseErrorCode {

    CURRENT_RELATIONSHIP_NOT_FOUND(
            HttpStatus.NOT_FOUND, "RELATIONSHIP404_1", "현재 활성화된 관계가 없습니다."),
    RELATIONSHIP_STATUS_NOT_FOUND(
            HttpStatus.NOT_FOUND, "RELATIONSHIP404_2", "관계 통계 정보가 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
