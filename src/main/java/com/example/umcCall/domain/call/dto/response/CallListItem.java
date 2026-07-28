package com.example.umcCall.domain.call.dto.response;

import com.example.umcCall.domain.call.enums.CallSender;
import com.example.umcCall.domain.call.enums.CallStatus;
import java.time.LocalDateTime;

/**
 * 통화 목록 한 줄. 리포지토리 JPQL 생성자 프로젝션 대상이라 top-level record로 둔다(N+1 회피).
 *
 * @param callId        통화 ID
 * @param characterName 상대 캐릭터 이름(firstName만 — 채팅과 동일 규약)
 * @param sender        발신자(USER | AI)
 * @param aiSummary     AI 통화 요약. ⚠ 요약 생성 로직 미구현이라 현재 항상 null(응답에서 키 생략됨)
 * @param createdAt     통화 발신 시각. 표시용 시각으로 startedAt 대신 이걸 쓴다(미연결 통화도 항상 존재)
 * @param status        통화 상태(COMPLETED | CANCELED | MISSED | REJECTED 중 하나만 조회됨)
 */
public record CallListItem(
        Long callId,
        String characterName,
        CallSender sender,
        String aiSummary,
        LocalDateTime createdAt,
        CallStatus status
) {
}
