package com.example.umcCall.domain.call.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "착신 대기(RINGING) 통화 단건. 걸려온 전화가 없으면 result 자체가 내려가지 않는다.")
public record CallIncomingResponse(
        @Schema(description = "받기(accept)·거절(reject)에 쓸 통화 ID", example = "12")
        Long callId,

        @Schema(description = "전화를 건 캐릭터 ID", example = "3")
        Long characterId,

        @Schema(description = "캐릭터 이름(firstName만 — 목록·채팅과 동일 규약)", example = "유나")
        String characterName,

        @Schema(description = "캐릭터 이미지 URL. 미설정이면 응답에서 키가 생략된다",
                example = "https://callfromai-images.s3.ap-northeast-2.amazonaws.com/preset-images/female_2.png",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String characterImage,

        @Schema(description = "벨이 울리기 시작한 시각. 부재중(MISSED) 판정도 이 시각을 기준으로 한다",
                example = "2026-08-06T20:30:00")
        LocalDateTime createdAt
) {
}
