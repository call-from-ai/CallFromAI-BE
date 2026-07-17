package com.example.umcCall.domain.character.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

/**
 * 캐릭터 목록 조회(마이페이지 - 캐릭터 관리)에 쓰이는 요약 응답.
 */
@Getter
@Builder
public class CharacterSummaryResponse {

    private Long characterId;
    private String name;
    private String imageUrl;
    private boolean main;
    private LocalDateTime createdAt;
    private LocalDate startedAt;
    private LocalDateTime lastMessageAt;
}
