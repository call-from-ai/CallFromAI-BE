package com.example.umcCall.domain.call.dto.response;

import com.example.umcCall.domain.call.enums.CallStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 통화 접속 티켓 발급 응답. 사용자 발신({@code POST /calls})과 착신 수락
 * ({@code PATCH /calls/{callId}/accept})이 공유한다 — 둘 다 "이 통화에 붙는 WS 입장권 발급"이고
 * 핸드셰이크 이후 흐름도 같다. 프론트는 wsTicket을 WS URL 쿼리로 되돌려 보낸다.
 *
 * @param callId     통화 기록 ID
 * @param callStatus 응답 시점 상태 — 발신 DIALING / 수락 PENDING
 * @param wsTicket   단명·1회용 WebSocket 티켓
 */
@Schema(description = "통화 접속 티켓 발급 응답. 발신(POST /calls)과 착신 수락(PATCH /calls/{callId}/accept)이 공유한다.")
public record CallTicketResponse(
        @Schema(description = "통화 기록 ID. 이후 종료·조회 API에 그대로 쓴다", example = "12")
        Long callId,

        @Schema(description = "응답 시점의 통화 상태. 발신은 DIALING, 착신 수락은 PENDING(받았지만 아직 WS 연결 전)",
                example = "DIALING", allowableValues = {"DIALING", "PENDING"})
        CallStatus callStatus,

        @Schema(description = """
                WebSocket 접속용 1회용 티켓(발급 후 30초 유효). `wss://{host}/ws/call?ticket={wsTicket}` 형태로 되돌려 보낸다.
                오디오는 서버가 CALL_READY를 보낸 뒤부터 유효하다 — 그전 프레임은 버려진다.""",
                example = "b7f1c2d0-3a8e-4f5b-9c1d-2e6a7b8c9d0e")
        String wsTicket
) {}
