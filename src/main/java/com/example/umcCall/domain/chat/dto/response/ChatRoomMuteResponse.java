package com.example.umcCall.domain.chat.dto.response;

/**
 * 채팅방 음소거 설정 결과. 갱신된 음소거 상태를 그대로 반환한다.
 */
public record ChatRoomMuteResponse(boolean isMuted) {
}
