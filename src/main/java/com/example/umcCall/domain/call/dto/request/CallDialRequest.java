package com.example.umcCall.domain.call.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * 사용자 발신(dial) 요청. memberId는 JWT에서 오므로 통화 상대 characterId만 받는다.
 */
public record CallDialRequest(
        @NotNull(message = "통화할 캐릭터 ID는 필수입니다.") Long characterId
) {
}
