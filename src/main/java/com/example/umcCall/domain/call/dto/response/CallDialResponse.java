package com.example.umcCall.domain.call.dto.response;

import com.example.umcCall.domain.call.enums.CallStatus;

/**
 * 사용자 발신(dial) 응답. 프론트는 wsTicket을 WS URL 쿼리로 되돌려 보내 핸드셰이크를 연다.
 *
 * @param callId   생성된 통화 기록 ID
 * @param wsTicket 단명·1회용 WebSocket 티켓
 */
public record CallDialResponse(
        Long callId,
        CallStatus callStatus,
        String wsTicket
) {}
