package com.example.umcCall.domain.chat.dto.response;

import java.time.LocalDateTime;
import lombok.Builder;

/**
 * 채팅방 목록의 방 1개를 표현하는 응답.
 *
 * @param chatRoomId           채팅방 ID
 * @param characterName        캐릭터 이름(캐릭터 정보 없으면 null)
 * @param characterProfileUrl  캐릭터 프로필 URL(없으면 null)
 * @param isMain               메인 연인 여부(최상단 정렬 대상)
 * @param isMuted              음소거 여부
 * @param lastMessage          마지막 메시지 미리보기(사진만이면 "[사진]", 없으면 null)
 * @param lastMessageAt        마지막(보이는) 메시지 시각(없으면 null)
 * @param unreadCount          안 읽은 수신 메시지 수
 */
@Builder
public record ChatRoomSummaryResponse(
        Long chatRoomId,
        String characterName,
        String characterProfileUrl,
        boolean isMain,
        boolean isMuted,
        String lastMessage,
        LocalDateTime lastMessageAt,
        long unreadCount
) {
}
