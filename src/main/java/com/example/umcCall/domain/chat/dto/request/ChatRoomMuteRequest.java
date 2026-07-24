package com.example.umcCall.domain.chat.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * 채팅방 음소거 설정 요청.
 * @param isMuted 음소거 여부. 누락 시 false로 잘못 해제되는 걸 막으려고 래퍼 타입 + @NotNull
 */
public record ChatRoomMuteRequest(
        @NotNull(message = "음소거 여부는 필수입니다.")
        Boolean isMuted
) {
}
