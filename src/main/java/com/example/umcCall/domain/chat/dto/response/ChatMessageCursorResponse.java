package com.example.umcCall.domain.chat.dto.response;

import java.util.List;

/**
 * 메시지 조회 커서 페이지네이션 응답.
 * content는 과거가 점점 뒷페이지로
 * nextCursor는 다음(과거) 요청에 쓸 커서(더 없으면 null).
 */
public record ChatMessageCursorResponse(
        List<ChatMessageResponse> content,
        Long nextCursor,
        boolean hasNext
) {
    public static ChatMessageCursorResponse of(List<ChatMessageResponse> content, Long nextCursor, boolean hasNext) {
        return new ChatMessageCursorResponse(content, nextCursor, hasNext);
    }
}
