package com.example.umcCall.domain.chat.port;

/**
 * 채팅방 목록 카드에 필요한 캐릭터 요약 정보.
 * relationship / character·character_image 도메인에서 조립되어 넘어온다.
 *
 * @param characterName 캐릭터 이름
 * @param profileUrl    캐릭터 프로필 사진 URL
 * @param isMain        메인 연인 여부
 */
public record CharacterSummary(
        String characterName,
        String profileUrl,
        boolean isMain
) {
}
