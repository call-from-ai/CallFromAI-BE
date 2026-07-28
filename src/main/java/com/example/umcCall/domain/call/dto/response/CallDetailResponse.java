package com.example.umcCall.domain.call.dto.response;

import com.example.umcCall.domain.call.entity.Call;
import java.time.LocalDateTime;

/**
 * 통화 기록 상세 조회 응답. 통화 목록에서 한 건을 탭해 들어오는 화면용 메타데이터.
 * 전문(script)은 별도 엔드포인트({@code GET /calls/{callId}/script})라 여기엔 담지 않는다.
 *
 * <p>⚠ {@code aiSummary}·{@code audioUrl}은 <b>생성 로직 미구현이라 현재 항상 null</b>(후순위).
 * 전역 Jackson {@code non_null} 설정으로 <b>null이면 응답 키가 생략</b>된다 — 명세서 예시처럼 값이
 * 채워지려면 요약/오디오 생성이 붙어야 한다. 이 API는 컬럼→응답 배관만 담당한다.
 *
 * <p>표시 시각은 {@code startedAt}이 아니라 <b>{@code createdAt}</b>(통화 목록과 동일 규약) —
 * 미연결 통화도 항상 존재해 시각이 비지 않는다. 캐릭터 이름은 목록과 같이 {@code firstName}만.
 *
 * @param characterName 상대 캐릭터 이름(firstName만 — 목록·채팅과 동일 규약)
 * @param aiSummary     AI 통화 요약(현재 항상 null → 키 생략)
 * @param createdAt     통화 발신 시각(표시용 — 미연결 통화도 항상 존재)
 * @param audioUrl      통화 오디오 URL(현재 항상 null → 키 생략)
 */
public record CallDetailResponse(
        String characterName,
        String aiSummary,
        LocalDateTime createdAt,
        String audioUrl
) {
    public static CallDetailResponse of(Call call) {
        return new CallDetailResponse(
                call.getRelationship().getCharacter().getFirstName(),
                call.getAiSummary(),
                call.getCreatedAt(),
                call.getAudioUrl());
    }
}
