package com.example.umcCall.domain.call.dto.response;

import com.example.umcCall.domain.call.entity.Call;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * 통화 종료 응답. 종료 화면("N분 M초 통화")을 서버 계산값으로 그릴 수 있게 준다 —
 * {@code callTime}은 {@code startedAt~endedAt} 기준이라 프론트 자체 측정과 어긋나지 않는다.
 *
 * @param callId   종료한 통화 ID
 * @param callTime 통화 시간(초)
 * @param endedAt  종료 시각
 */
@Schema(description = "통화 종료 응답. 종료 화면(\"N분 M초 통화\")을 서버 계산값으로 그릴 수 있게 준다.")
public record CallEndResponse(
        @Schema(description = "종료한 통화 ID", example = "12")
        Long callId,

        @Schema(description = "통화 시간(초). startedAt~endedAt 기준이라 프론트 자체 측정과 어긋나지 않는다",
                example = "137")
        Integer callTime,

        @Schema(description = "종료 시각", example = "2026-08-06T20:32:17")
        LocalDateTime endedAt
) {
    public static CallEndResponse of(Call call) {
        return new CallEndResponse(call.getId(), call.getCallTime(), call.getEndedAt());
    }
}
