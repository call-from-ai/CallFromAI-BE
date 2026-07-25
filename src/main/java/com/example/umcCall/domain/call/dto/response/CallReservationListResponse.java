package com.example.umcCall.domain.call.dto.response;

import java.util.List;

/**
 * 내 통화 예약 목록 응답. <b>오늘 하루</b>의 대기 중(SCHEDULED) 예약을 <b>가까운 시각부터</b> 담는다.
 * 창은 당일 00:00 ~ 다음날 새벽 5시 — 체감상 "오늘 밤"인 새벽 예약이 자정을 넘겼다고 사라지지 않게 한다.
 *
 * <p>⚠ 통화 목록({@code GET /calls})은 최신순(과거로 내려감)인데 여기는 <b>정렬 방향이 반대</b>다 —
 * 예약은 미래의 약속이라 "곧 올 전화"가 위에 있어야 한다.
 *
 * <p>페이지네이션 없음 — 하루치라 유한하다(통화 전문과 같은 판단).
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
