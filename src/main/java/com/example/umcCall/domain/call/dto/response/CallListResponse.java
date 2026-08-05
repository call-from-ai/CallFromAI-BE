package com.example.umcCall.domain.call.dto.response;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 내 통화 목록 응답 래퍼. 최근 종료된 통화 최대 20건을 최신순(createdAt DESC)으로 담는다.
 * 페이지네이션 없음(프론트와 20건 고정 합의) — 그래서 page/size/hasNext 없이 content만 둔다.
 *
 * @param content 통화 목록(최신순, 최대 20)
 */
@Schema(description = "내 통화 목록 응답. 페이지네이션 없이 최근 20건 고정이라 page/size/hasNext가 없다.")
public record CallListResponse(
        @ArraySchema(arraySchema = @Schema(
                description = "종료된 통화 목록(최신순, 최대 20건). 통화 기록이 없으면 빈 배열"))
        List<CallListItem> content
) {
    public static CallListResponse of(List<CallListItem> content) {
        return new CallListResponse(content);
    }
}
