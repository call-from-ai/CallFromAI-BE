package com.example.umcCall.domain.call.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * 통화 예약 수정 요청. 현재 바꿀 수 있는 건 예약 시각뿐이다.
 *
 * <p>과거 시각은 {@code @Future}가 막는다(전역 핸들러가 {@code COMMON400_1} + 필드 메시지로 응답) —
 * 도메인 에러코드를 따로 두지 않는다. 이미 지난 시각으로 옮기면 스케줄러가 grace window 밖으로 보고
 * 곧 예약을 종결시켜 버리므로 애초에 받지 않는 게 맞다.
 *
 * @param scheduledAt 새 예약 시각(미래여야 한다)
 */
public record CallReservationUpdateRequest(
        @NotNull(message = "예약 시각은 필수입니다.")
        @Future(message = "예약 시각은 미래여야 합니다.")
        LocalDateTime scheduledAt
) {
}
