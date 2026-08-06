package com.example.umcCall.domain.call.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 사용자 발신(dial) 요청. memberId는 JWT에서 오므로 통화 상대 characterId만 받는다.
 */
public record CallDialRequest(
        @Schema(description = "통화할 캐릭터 ID. 본인의 메인(활성) 캐릭터여야 한다 — 메인이 아니면 400, 남의 캐릭터면 403",
                example = "3")
        @NotNull(message = "통화할 캐릭터 ID는 필수입니다.") Long characterId
) {
}
