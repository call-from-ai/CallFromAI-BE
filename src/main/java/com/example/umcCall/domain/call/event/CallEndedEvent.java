package com.example.umcCall.domain.call.event;

import com.example.umcCall.domain.call.enums.CallEndReason;

/**
 * 통화가 <b>서비스 쪽에서</b> 마감됐다(사용자 REST 종료 / 시간 상한 스위퍼). 아직 살아 있는 WebSocket 세션·
 * STT 스트림·워커를 정리하고 클라이언트에 {@code CALL_ENDED}를 통지하라는 신호로, WS 핸들러가 받는다.
 *
 * <p>⚠ 포트 직접 호출이 아니라 이벤트인 이유: 세션 맵을 가진 WS 핸들러가 이미 {@code CallService}에
 * 의존해서, 서비스가 핸들러를 주입받으면 <b>순환 참조로 앱이 기동하지 않는다</b>.
 * 수신은 {@code AFTER_COMMIT}이다 — 마감이 롤백되면 통화가 유지돼야 하므로 소켓도 살아 있어야 한다.
 *
 * <p>⚠ {@code callTime}을 payload에 싣는 이유: 수신이 커밋 뒤라 리스너가 {@code Call}을 다시 읽으면
 * 쿼리가 한 번 더 나가고, 통지 값이 마감 시점과 어긋날 여지가 생긴다. 마감한 쪽이 아는 값을 그대로 넘긴다.
 *
 * @param callId   종료된 통화 ID
 * @param reason   종료 사유(프론트 종료 화면 문구의 근거)
 * @param callTime 통화 시간(초). {@code startedAt~endedAt} 기준이라 REST {@code CallEndResponse.callTime}과 같은 값이다.
 */
public record CallEndedEvent(Long callId, CallEndReason reason, Integer callTime) {
}
