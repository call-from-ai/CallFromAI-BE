package com.example.umcCall.domain.call.ticket;

/**
 * WebSocket 핸드셰이크에 실려 세션에 바인딩되는 신원.
 * <p>ID만 담고 스냅샷(character/relationship)은 담지 않는다 — 스냅샷은 chat() 시점에 최신값으로
 * 다시 만들어야 하고(특히 stale-version 가드는 통화 중 최신 version을 봐야 함), 여기 얼려두면 안 된다.
 *
 * @param callId         전사·통화기록을 붙일 대상 Call
 * @param relationshipId chat 컨텍스트 + stale-version 가드의 기준 관계
 * @param characterId    통화 상대 캐릭터(로그·편의용, relationship에서도 도달 가능)
 */
public record WsTicket(
        Long callId,
        Long relationshipId,
        Long characterId
) {}
