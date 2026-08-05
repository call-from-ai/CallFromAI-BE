package com.example.umcCall.domain.call.dto.response;

import com.example.umcCall.domain.call.enums.CallSender;
import com.example.umcCall.domain.call.enums.CallStatus;
import com.example.umcCall.domain.call.enums.CallSummaryStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * 통화 목록 한 줄. 리포지토리 JPQL 생성자 프로젝션 대상이라 top-level record로 둔다(N+1 회피).
 *
 * @param callId        통화 ID
 * @param characterName 상대 캐릭터 이름(firstName만 — 채팅과 동일 규약)
 * @param sender        발신자(USER | AI)
 * @param aiSummary     통화 주제 라벨(한 문장). {@code summaryStatus=READY}일 때만 있다
 * @param summaryStatus 요약 준비 상태. <b>항상 내려간다</b>(값이 없으면 {@code NONE}).
 *                      ⚠ {@code aiSummary} null만 보고 "요약 없음"으로 그리면 준비 중인 요약을 놓친다 —
 *                      재시도가 의미 있는 건 {@code PROCESSING}뿐이다
 * @param createdAt     통화 발신 시각. 표시용 시각으로 startedAt 대신 이걸 쓴다(미연결 통화도 항상 존재)
 * @param status        통화 상태(COMPLETED | CANCELED | MISSED | REJECTED 중 하나만 조회됨)
 */
@Schema(description = "통화 목록 한 줄(종료된 통화만).")
public record CallListItem(
        @Schema(description = "통화 ID. 상세·전사 조회에 그대로 쓴다", example = "12")
        Long callId,

        @Schema(description = "상대 캐릭터 이름(firstName만 — 채팅과 동일 규약)", example = "유나")
        String characterName,

        @Schema(description = "발신자. USER(사용자가 검), AI(AI가 먼저 검)",
                example = "USER", allowableValues = {"USER", "AI"})
        CallSender sender,

        @Schema(description = "통화 주제 라벨(한 문장, 20자 내외). summaryStatus=READY일 때만 있고, 없으면 응답에서 키가 생략된다",
                example = "오늘 하루와 퇴근 후 일상 이야기",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String aiSummary,

        @Schema(description = """
                요약 준비 상태. 값이 없으면 NONE으로 **항상 내려간다**.
                aiSummary 유무만 보고 "요약 없음"으로 그리면 준비 중인 요약을 놓친다 — 재조회가 의미 있는 건 PROCESSING뿐이다.
                NONE(요약할 대화가 없음) / PROCESSING(생성 중) / READY(완료) / FAILED(생성 실패, 재시도해도 안 생김)""",
                example = "READY", allowableValues = {"NONE", "PROCESSING", "READY", "FAILED"})
        CallSummaryStatus summaryStatus,

        @Schema(description = "통화 발신 시각(표시용). 연결되지 못한 통화에도 항상 존재한다",
                example = "2026-08-06T20:30:00")
        LocalDateTime createdAt,

        @Schema(description = """
                통화 상태. 목록에는 종료된 통화만 나온다.
                COMPLETED(통화함) / CANCELED(연결 실패) / MISSED(부재중) / REJECTED(거절함)""",
                example = "COMPLETED",
                allowableValues = {"COMPLETED", "CANCELED", "MISSED", "REJECTED"})
        CallStatus status
) {
}
