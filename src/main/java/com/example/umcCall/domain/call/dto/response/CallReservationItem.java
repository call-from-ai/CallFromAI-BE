package com.example.umcCall.domain.call.dto.response;

import java.time.LocalDateTime;

/**
 * 통화 예약 목록 한 줄. 리포지토리 JPQL 생성자 프로젝션 대상이라 top-level record로 둔다(N+1 회피).
 *
 * <p>상태({@code SCHEDULED})는 담지 않는다 — 목록이 대기 중 예약만 반환하므로 정보량이 없다.
 * 지난 예약(FIRED)의 결과는 통화 기록({@code GET /calls})에서 본다.
 *
 * @param reservationId  수정에 쓸 예약 ID({@code PATCH /call-reservations/{reservationId}})
 * @param characterId    통화할 캐릭터 ID
 * @param characterName  캐릭터 이름(firstName만 — 목록·채팅과 동일 규약)
 * @param characterImage 캐릭터 이미지 URL. 미설정이면 null → 응답에서 키 생략
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
