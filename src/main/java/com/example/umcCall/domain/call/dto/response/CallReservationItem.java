package com.example.umcCall.domain.call.dto.response;

import java.time.LocalDateTime;

/**
 * 통화 예약 목록 한 줄. JPQL 생성자 프로젝션 대상이라 top-level record로 둔다(N+1 회피).
 * 상태는 담지 않는다 — 목록이 대기 중 예약만 반환한다.
 *
 * @param reservationId  수정에 쓸 예약 ID
 * @param characterId    통화할 캐릭터 ID
 * @param characterName  캐릭터 이름(firstName만 — 목록·채팅과 동일 규약)
 * @param characterImage 캐릭터 이미지 URL. 미설정이면 null → 키 생략
 * @param scheduledAt    예약된 발신 시각
 */
public record CallReservationItem(
        Long reservationId,
        Long characterId,
        String characterName,
        String characterImage,
        LocalDateTime scheduledAt
) {
}
