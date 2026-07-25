package com.example.umcCall.domain.call.dto.response;

import java.util.List;

/**
 * 내 통화 목록 응답 래퍼. 최근 종료된 통화 최대 20건을 최신순(createdAt DESC)으로 담는다.
 * 페이지네이션 없음(프론트와 20건 고정 합의) — 그래서 page/size/hasNext 없이 content만 둔다.
 *
 * @param content 통화 목록(최신순, 최대 20)
 */
public record CallListResponse(
        List<CallListItem> content
) {
    public static CallListResponse of(List<CallListItem> content) {
        return new CallListResponse(content);
    }
}
