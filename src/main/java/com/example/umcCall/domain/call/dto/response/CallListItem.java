package com.example.umcCall.domain.call.dto.response;

import com.example.umcCall.domain.call.enums.CallSender;
import com.example.umcCall.domain.call.enums.CallStatus;
import com.example.umcCall.domain.call.enums.CallSummaryStatus;
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
public record CallListItem(
        Long callId,
        String characterName,
        CallSender sender,
        String aiSummary,
        CallSummaryStatus summaryStatus,
        LocalDateTime createdAt,
        CallStatus status
) {
}
