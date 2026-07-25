package com.example.umcCall.domain.call.dto.response;

import com.example.umcCall.domain.call.enums.CallStatus;

/**
 * 통화 접속 티켓 발급 응답. 사용자 발신({@code POST /calls})과 착신 수락
 * ({@code PATCH /calls/{callId}/accept})이 공유한다 — 둘 다 "이 통화에 붙는 WS 입장권 발급"이고
 * 핸드셰이크 이후 흐름도 같다. 프론트는 wsTicket을 WS URL 쿼리로 되돌려 보낸다.
 *
 * @param callId     통화 기록 ID
 * @param callStatus 응답 시점 상태 — 발신 DIALING / 수락 PENDING
 * @param wsTicket   단명·1회용 WebSocket 티켓
 */
public record CallTicketResponse(
        Long callId,
        CallStatus callStatus,
        String wsTicket
) {}
