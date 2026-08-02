package com.example.umcCall.domain.call.event;

import java.time.LocalDateTime;

/**
 * AI 발신을 사용자가 받지 않아 부재중으로 마감됐다({@code RINGING} → {@code MISSED}). 알림 도메인이
 * 부재중 알림을 만들 근거로 쓰는 <b>사실 통지</b>다.
 *
 * <p>⚠ {@link CallEndedEvent}와 성격이 다르다 — 저쪽은 "살아 있는 세션을 정리하라"는 <b>명령</b>이라
 * 수신자가 없으면 오디오가 계속 흘러 STT 비용이 샌다. 이쪽은 소켓이 열린 적 없는 상태({@code RINGING})라
 * 정리할 세션이 없고, 구독자가 없어도 통화 도메인은 정상이다.
 *
 * <p>⚠ 수신은 {@code @TransactionalEventListener(AFTER_COMMIT)}로 할 것 — 마감이 롤백되면 통화는 여전히
 * 벨이 울리는 중인데 부재중 알림만 남는다. 반대로 알림 생성 실패가 마감을 되돌려도 안 되니 리스너는
 * 예외를 삼키고 로깅한다(도메인 관례).
 *
 * <p>⚠ 발행 조건은 <b>상태가 실제로 전이됐을 때뿐</b>이다. 스위퍼는 락 없이 고른 뒤 락을 잡고 상태를
 * 재확인하므로 그 사이 사용자가 받은 통화는 no-op으로 빠진다 — 그때 발행하면 받은 전화가 부재중 알림으로 뜬다.
 *
 * @param callId         부재중으로 마감된 통화 ID
 * @param relationshipId 통화가 속한 관계 ID({@code SystemNotification.relationshipId}, 캐릭터 이름 조회의 출발점)
 * @param memberId       알림을 받을 회원 ID. 관계에서 꺼내 실어 보낸다 — {@code SystemNotification}의 필수
 *                       값이자 홈화면 조회 키라, 없으면 리스너가 커밋 뒤에 관계를 다시 읽어야 한다.
 * @param occurredAt     이벤트 발생(부재중 확정) 시각. 홈화면 알림 목록의 정렬·7일 필터 기준이 되는
 *                       {@code SystemNotification.occurredAt}에 그대로 들어갈 값이다.
 */
public record CallMissedEvent(Long callId, Long relationshipId, Long memberId, LocalDateTime occurredAt) {
}
