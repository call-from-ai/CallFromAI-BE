package com.example.umcCall.domain.call.dto.response;

import com.example.umcCall.domain.call.entity.CallHistory;
import com.example.umcCall.domain.call.enums.CallSpeaker;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 통화 전사(script) 조회 응답. 통화 하나는 유한한 한 대화라 전문을 통째로(과거→최신, id ASC) 반환한다.
 * 커서 페이지네이션은 두지 않는다 — 비정상적으로 긴 통화가 데이터로 잡히면 그때 승격(후순위).
 *
 * @param callId 조회한 통화 ID
 * @param lines  발화 순서(id ASC)대로의 전사 줄. 통화에 아직 전사가 없으면 빈 리스트.
 */
public record CallScriptResponse(
        Long callId,
        List<Line> lines
) {

    /**
     * 전사 한 줄.
     *
     * @param speaker   발화자(USER | AI)
     * @param content   발화 내용
     * @param createdAt 발화 시각(= 저장 시각. 저장이 발화 순간마다 일어나 실제 시각과 일치)
     */
    public record Line(
            CallSpeaker speaker,
            String content,
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
