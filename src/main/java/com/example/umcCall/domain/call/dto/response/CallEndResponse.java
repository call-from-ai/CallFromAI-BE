package com.example.umcCall.domain.call.dto.response;

import com.example.umcCall.domain.call.entity.Call;
import java.time.LocalDateTime;

/**
 * 통화 종료 응답. 종료 화면("N분 M초 통화했습니다")을 서버가 계산한 값으로 그릴 수 있게 준다.
 *
 * <p>⚠ 상태만 바꾸는 다른 API들({@code reject}·{@code reschedule})은 {@code ApiResponse<Void>}인데
 * 여기는 결과를 담는다 — {@code callTime}은 서버가 {@code startedAt}~{@code endedAt}으로 계산한 값이라
 * 프론트가 자체 측정하면 미세하게 어긋난다.
 *
 * @param callId   종료한 통화 ID
 * @param callTime 통화 시간(초). 연결~종료 구간을 서버가 계산한 값
 * @param endedAt  종료 시각
 */
public record CallEndResponse(
        Long callId,
        Integer callTime,
        LocalDateTime endedAt
) {
    public static CallEndResponse of(Call call) {
        return new CallEndResponse(call.getId(), call.getCallTime(), call.getEndedAt());
    }
}
