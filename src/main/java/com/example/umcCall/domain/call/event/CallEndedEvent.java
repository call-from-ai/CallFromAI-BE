package com.example.umcCall.domain.call.event;

/**
 * 사용자가 REST로 통화를 끝냈다({@code PATCH /calls/{callId}/end}). 아직 살아 있는 WebSocket 세션·STT
 * 스트림·워커를 정리하라는 신호로, WS 핸들러가 받는다.
 *
 * <p>⚠ <b>왜 포트 직접 호출이 아니라 이벤트인가</b>: 세션 맵을 가진 주체는 WS 핸들러인데 그 핸들러가 이미
 * {@code CallService}에 의존한다(connect/finish 위임). 서비스가 핸들러를 다시 주입받으면
 * <b>순환 참조로 앱이 기동하지 않는다</b>(Spring Boot 기본 설정). 이벤트는 그 방향 의존을 없앤다 —
 * 서비스는 "끝났다"만 알리고, 세션을 아는 쪽이 반응한다.
 *
 * <p>수신은 {@code @TransactionalEventListener(AFTER_COMMIT)}다 — 상태 마감이 커밋된 뒤에만 소켓을
 * 닫는다. 트랜잭션이 롤백되면 통화는 계속 유지돼야 하므로 소켓도 살아 있어야 한다.
 *
 * @param callId 종료된 통화 ID
 */
public record CallEndedEvent(Long callId) {
}
