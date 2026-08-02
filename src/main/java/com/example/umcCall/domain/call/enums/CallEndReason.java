package com.example.umcCall.domain.call.enums;

/**
 * 통화가 정상적으로 끝난 이유. WS {@code CALL_ENDED}의 {@code reason}으로 나가고, 프론트는 이 값으로
 * 종료 화면 문구를 고른다(사용자가 끊음 vs 시간이 초과됨).
 *
 * <p>⚠ 값은 실제 트리거가 있는 것만 둔다 — 아무도 보내지 않는 값을 미리 정의하면 프론트가 처리 분기를
 * 만들어 놓고 영영 타지 않는다. AI가 대화 중 스스로 끊는 {@code AI_ENDED}는 그 기능이 생길 때 추가한다.
 *
 * <p>비정상 종료는 이 열거형이 아니라 {@code ERROR} 메시지로 통지한다.
 */
public enum CallEndReason {
    /** 사용자가 끊었다({@code PATCH /calls/{callId}/end}). REST 응답으로도 알 수 있지만, 정상 종료 앞엔 항상 통지가 있다는 계약을 위해 함께 보낸다. */
    USER_ENDED,
    /** 통화 시간 상한({@code call.timeout.max-call-minutes})을 넘겨 서버가 마감했다. AI의 의도가 아니라 운영 안전망이다. */
    TIMEOUT
}
