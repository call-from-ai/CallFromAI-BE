package com.example.umcCall.domain.chat.dto.response;

/**
 * 채팅방 진입 시 헤더에 띄우는 정보(캐릭터 이름·사진, 며칠째, 전화버튼 노출 여부).
 * 채팅방 목록(CharacterSummary)과는 별개의 응답이다.
 *
 * @param chatRoomId          채팅방 ID
 * @param characterId         통화하기 호출 등에 쓰는 캐릭터 ID
 * @param characterFirstName  캐릭터 이름(firstName만)
 * @param characterImageUrl   캐릭터 프로필 사진(없으면 null)
 * @param isMain              메인 연인 여부(전화버튼 노출 기준)
 * @param dDay                관계 시작일 기준 며칠째(당일=1)
 */
public record CharacterRoomHeader(
        Long chatRoomId,
        Long characterId,
        String characterFirstName,
        String characterImageUrl,
        boolean isMain,
        int dDay
) {
}
