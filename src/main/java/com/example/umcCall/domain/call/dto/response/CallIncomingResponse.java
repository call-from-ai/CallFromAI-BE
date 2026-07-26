package com.example.umcCall.domain.call.dto.response;

import java.time.LocalDateTime;

/**
 * 착신 대기 통화 조회 응답. 착신은 하나뿐이라 <b>단건</b>이고, 없으면 null이라 응답에서 키가 생략된다.
 *
 * <p>⚠ 회원 기준 RINGING이 2건 생기는 엣지가 있다 — 발신 중복 방어가 관계 단위라, 이전 메인 캐릭터의
 * 착신이 안 닫힌 채 메인이 바뀌면 새 관계로 또 발신된다. 그 경우 가장 최근 1건을 주고 남은 건 스위퍼가 닫는다.
 *
 * @param callId         받기/거절에 쓸 통화 ID
 * @param characterId    전화를 건 캐릭터 ID
 * @param characterName  캐릭터 이름(firstName만 — 목록·채팅과 동일 규약)
 * @param characterImage 캐릭터 이미지 URL. 미설정이면 null → 키 생략
 * @param createdAt      벨이 울리기 시작한 시각. 부재중 스위퍼도 같은 값을 기준으로 삼는다
 */
public record CallIncomingResponse(
        Long callId,
        Long characterId,
        String characterName,
        String characterImage,
        LocalDateTime createdAt
) {
}
