package com.example.umcCall.domain.call.event;

/**
 * AI 발신으로 착신 통화가 생성돼 벨이 울리기 시작했다({@code RINGING}). 알림 도메인이 착신 푸시를
 * 보낼 근거로 쓰는 <b>사실 통지</b>다({@link CallMissedEvent}와 같은 성격 — 구독자가 없어도 통화
 * 도메인은 정상이고, 프론트는 {@code GET /calls/incoming}으로 착신을 발견한다).
 *
 * <p>⚠ 수신은 {@code @TransactionalEventListener(AFTER_COMMIT)}로 할 것 — 통화 생성이 롤백되면
 * 통화는 없는데 벨만 울린다.
 *
 * <p>⚠ 푸시가 전달되지 않아도 <b>통화 상태는 건드리지 않는다.</b> {@code CANCELED}는 사용자 행동
 * (발신·받기)이 선행된 뒤의 서버 측 연결 실패라, 사용자 행동이 없는 이 경로에 쓰면 의미가 어긋난다.
 * 푸시를 못 받은 사용자는 벨 타임아웃 뒤 {@code MISSED}가 된다.
 *
 * <p>캐릭터 정보를 싣는 이유: 발행 시점({@code ring()})에 이미 로드돼 있고 값이 FCM data로 그대로
 * 들어가서다. 리스너가 커밋 뒤 다시 읽으면 쿼리만 늘고 발행 시점과 어긋난다.
 *
 * @param callId             착신 통화 ID(받기/거절 대상)
 * @param relationshipId     통화가 속한 관계 ID. 리스너가 채팅방을 찾는 출발점이다
 * @param memberId           푸시를 받을 회원 ID
 * @param characterId        전화를 건 캐릭터 ID
 * @param characterName      캐릭터 이름(firstName만 — 목록·채팅·착신 조회와 동일 규약)
 * @param characterImageUrl  캐릭터 이미지 URL. 이미지 없이 만든 캐릭터면 null이다
 */
public record CallRingingEvent(
        Long callId,
        Long relationshipId,
        Long memberId,
        Long characterId,
        String characterName,
        String characterImageUrl
) {
}
