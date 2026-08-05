package com.example.umcCall.domain.call.dto.response;

import com.example.umcCall.domain.call.entity.CallHistory;
import com.example.umcCall.domain.call.enums.CallSpeaker;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 통화 전사(script) 조회 응답. 통화 하나는 유한한 한 대화라 전문을 통째로(과거→최신, id ASC) 반환한다.
 * 커서 페이지네이션은 두지 않는다 — 비정상적으로 긴 통화가 데이터로 잡히면 그때 승격(후순위).
 *
 * @param callId 조회한 통화 ID
 * @param lines  발화 순서(id ASC)대로의 전사 줄. 통화에 아직 전사가 없으면 빈 리스트.
 */
@Schema(description = "통화 전사(전문) 응답. 한 통화의 전문을 통째로 반환한다(페이지네이션 없음).")
public record CallScriptResponse(
        @Schema(description = "조회한 통화 ID", example = "12")
        Long callId,

        @ArraySchema(arraySchema = @Schema(
                description = "발화 순서(과거→최신)대로의 전사 줄. 전사가 없는 통화면 빈 배열"))
        List<Line> lines
) {

    /**
     * 전사 한 줄.
     *
     * @param speaker   발화자(USER | AI)
     * @param content   발화 내용
     * @param createdAt 발화 시각(= 저장 시각. 저장이 발화 순간마다 일어나 실제 시각과 일치)
     */
    @Schema(description = "전사 한 줄. AI 한 턴은 여러 문장이어도 한 줄로 합쳐 저장된다.")
    public record Line(
            @Schema(description = "발화자. USER(사용자) / AI(캐릭터)",
                    example = "AI", allowableValues = {"USER", "AI"})
            CallSpeaker speaker,

            @Schema(description = "발화 내용. 실제로 사용자에게 들린 대사만 남는다 — 끼어들기로 중단된 뒷문장은 저장되지 않는다",
                    example = "오늘 하루는 어땠어?")
            String content,

            @Schema(description = "발화 시각(= 저장 시각. 발화 순간마다 저장돼 실제 시각과 일치)",
                    example = "2026-08-06T20:30:12")
            LocalDateTime createdAt
    ) {
        private static Line from(CallHistory history) {
            return new Line(history.getSpeaker(), history.getContent(), history.getCreatedAt());
        }
    }

    public static CallScriptResponse of(Long callId, List<CallHistory> histories) {
        return new CallScriptResponse(callId, histories.stream().map(Line::from).toList());
    }
}
