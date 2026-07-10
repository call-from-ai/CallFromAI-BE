package com.example.umcCall.global.apiPayload;

import java.util.List;
import lombok.Getter;
import org.springframework.data.domain.Page;

/**
 * 목록/페이지 응답 공통 포맷 (오프셋 기반).
 * 커서 기반이 필요한 경우(예: 채팅 메시지 조회)는 별도의 커서 응답 형태를 둔다.
 */
@Getter
public class PageResponse<T> {

    private final List<T> content;
    private final int page;
    private final int size;
    private final long totalElements;
    private final int totalPages;
    private final boolean hasNext;

    private PageResponse(List<T> content, int page, int size, long totalElements, int totalPages, boolean hasNext) {
        this.content = content;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
        this.hasNext = hasNext;
    }

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext()
        );
    }
}
