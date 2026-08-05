package com.example.umcCall.domain.chat.dto.response;

/**
 * 특정 방의 상태만 알리는 가벼운 SSE 신호(로딩 시작 · 답장 실패).
 * 저장하지 않는 순간 신호라 방 번호만 담는다. 유저는 SSE 연결이 하나뿐이므로
 * 프론트는 이 chatRoomId로 어느 방의 신호인지 구분한다.
 */
public record ChatRoomSignalResponse(Long chatRoomId) {
}
