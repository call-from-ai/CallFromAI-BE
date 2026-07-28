package com.example.umcCall.domain.call.event;

/**
 * 사용자가 REST로 통화를 끝냈다({@code PATCH /calls/{callId}/end}). 아직 살아 있는 WebSocket 세션·STT
 * 스트림·워커를 정리하라는 신호로, WS 핸들러가 받는다.
 *
 * <p>⚠ 포트 직접 호출이 아니라 이벤트인 이유: 세션 맵을 가진 WS 핸들러가 이미 {@code CallService}에
 * 의존해서, 서비스가 핸들러를 주입받으면 <b>순환 참조로 앱이 기동하지 않는다</b>.
 * 수신은 {@code AFTER_COMMIT}이다 — 마감이 롤백되면 통화가 유지돼야 하므로 소켓도 살아 있어야 한다.
 *
 * @param callId 종료된 통화 ID
 */
public record CallEndedEvent(Long callId) {
}
