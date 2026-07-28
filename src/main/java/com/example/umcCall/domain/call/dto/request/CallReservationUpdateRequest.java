package com.example.umcCall.domain.call.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * 통화 예약 수정 요청. 현재 바꿀 수 있는 건 예약 시각뿐이다.
 * 과거 시각은 {@code @Future}가 막는다(도메인 에러코드를 따로 두지 않는다).
 *
 * @param scheduledAt 새 예약 시각(미래여야 한다)
 */
public record CallReservationUpdateRequest(
        @NotNull(message = "예약 시각은 필수입니다.")
        @Future(message = "예약 시각은 미래여야 합니다.")
        LocalDateTime scheduledAt
) {
}
