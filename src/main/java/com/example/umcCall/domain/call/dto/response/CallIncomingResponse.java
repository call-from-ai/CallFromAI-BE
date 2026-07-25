package com.example.umcCall.domain.call.dto.response;

import java.time.LocalDateTime;

/**
 * 착신 대기 통화 조회 응답. 앱이 켜졌을 때(또는 폴링으로) "지금 걸려온 전화"를 발견하는 경로다.
 *
 * <p><b>착신은 하나뿐이다</b> — 리스트가 아니라 단건으로 준다. 착신이 없으면 {@code result} 자체가
 * null이라 전역 {@code non_null} 설정으로 응답에서 키가 생략된다(프론트: result가 없으면 착신 화면 없음).
 *
 * <p>⚠ 회원 기준으로 RINGING이 2건 생기는 경로가 이론적으로 있다 — 발신 시 중복 방어가 <b>관계 단위</b>라,
 * 이전 메인 캐릭터의 착신이 안 닫힌 채 메인이 바뀌면 새 관계로 또 발신될 수 있다. 그 경우
 * <b>가장 최근 1건</b>을 주고(어느 걸 보여줄지 프론트가 고르게 하지 않는다), 남은 것은 부재중 스위퍼가 닫는다.
 *
 * <p>FCM 푸시가 붙은 뒤에도 이 API는 남는다 — 푸시를 놓쳤거나 앱이 죽어 있던 경우의 복구 경로다.
 *
 * @param callId         받기/거절에 쓸 통화 ID({@code PATCH /calls/{callId}/accept|reject})
 * @param characterId    전화를 건 캐릭터 ID
 * @param characterName  캐릭터 이름(firstName만 — 목록·채팅과 동일 규약)
 * @param characterImage 캐릭터 이미지 URL. 미설정이면 null → 응답에서 키 생략
 * @param createdAt      벨이 울리기 시작한 시각. 프론트가 남은 시간을 계산하는 기준이며,
 *                       부재중 판정 스위퍼도 같은 값을 기준으로 삼는다.
 */
public record CallIncomingResponse(
        Long callId,
        Long characterId,
        String characterName,
        String characterImage,
        LocalDateTime createdAt
) {
}
