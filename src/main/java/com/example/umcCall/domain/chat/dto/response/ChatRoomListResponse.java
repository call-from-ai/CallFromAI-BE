package com.example.umcCall.domain.chat.dto.response;

import java.util.List;

/**
 * 채팅방 목록 조회 응답 래퍼.
 * 방 목록은 최대 5개라 페이지네이션이 필요없어서 PageResponse 대신 content만 담는다.
 *
 * @param content 방 요약 목록(is_main 최상단 → 나머지 최신순)
 */
public record ChatRoomListResponse(
        List<ChatRoomSummaryResponse> content
) {
    public static ChatRoomListResponse of(List<ChatRoomSummaryResponse> content) {
        return new ChatRoomListResponse(content);
    }
}
