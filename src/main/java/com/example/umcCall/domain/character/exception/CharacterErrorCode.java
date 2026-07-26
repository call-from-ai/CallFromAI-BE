package com.example.umcCall.domain.character.exception;

import com.example.umcCall.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 캐릭터 도메인 에러 코드.
 */
@Getter
@RequiredArgsConstructor
public enum CharacterErrorCode implements BaseErrorCode {

    CHARACTER_NOT_FOUND(HttpStatus.NOT_FOUND, "CHARACTER404_1", "존재하지 않는 캐릭터입니다."),
    NO_ACTIVE_CHARACTER(HttpStatus.NOT_FOUND, "CHARACTER404_2", "활성화된 캐릭터가 없습니다."),
    CHARACTER_ACCESS_DENIED(HttpStatus.FORBIDDEN, "CHARACTER403_1", "본인의 캐릭터만 접근할 수 있습니다."),
    INVALID_PRESET_IMAGE(HttpStatus.BAD_REQUEST, "CHARACTER400_1", "존재하지 않는 프리셋 이미지입니다."),
    CHARACTER_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "CHARACTER400_3", "캐릭터는 최대 5개까지 생성할 수 있습니다."),
    CHARACTER_RECREATE_TOO_SOON(HttpStatus.BAD_REQUEST, "CHARACTER400_4", "캐릭터 생성 후 24시간이 지나야 다시 생성할 수 있습니다."),
    ACTIVE_CHARACTER_CHANGE_TOO_SOON(HttpStatus.BAD_REQUEST, "CHARACTER400_5", "캐릭터 전환 후 최소 3일이 지나야 변경할 수 있습니다."),
    CANNOT_DELETE_ACTIVE_CHARACTER(HttpStatus.BAD_REQUEST, "CHARACTER400_6", "메인으로 설정된 캐릭터는 삭제할 수 없습니다."),
    CHARACTER_DELETE_TOO_SOON(HttpStatus.BAD_REQUEST, "CHARACTER400_7", "캐릭터 생성 후 24시간이 지나야 삭제할 수 있습니다."),
    INVALID_TRAIT_SELECTION(HttpStatus.BAD_REQUEST, "CHARACTER400_8", "매력 키워드와 우선순위는 중복될 수 없고 1부터 연속되어야 합니다."),
    CHARACTER_ALREADY_EDITED(HttpStatus.BAD_REQUEST, "CHARACTER400_9", "캐릭터 정보는 최초 1회만 수정할 수 있습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
