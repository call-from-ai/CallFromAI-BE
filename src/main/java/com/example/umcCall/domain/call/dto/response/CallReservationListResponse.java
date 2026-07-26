package com.example.umcCall.domain.call.dto.response;

import java.util.List;

/**
 * 내 통화 예약 목록 응답. <b>오늘 하루</b>(당일 00:00 ~ 다음날 05:00)의 대기 중 예약을 가까운 시각부터 담는다.
 *
 * <p>⚠ 통화 목록({@code GET /calls})은 최신순인데 여기는 정렬 방향이 반대다 — 곧 올 전화가 위여야 한다.
 * 페이지네이션 없음(하루치라 유한하다).
 *
 * @param content 오늘 하루의 대기 중 예약(예약 시각 오름차순)
 */
public record CallReservationListResponse(
        List<CallReservationItem> content
) {
    public static CallReservationListResponse of(List<CallReservationItem> content) {
        return new CallReservationListResponse(content);
    }
}
