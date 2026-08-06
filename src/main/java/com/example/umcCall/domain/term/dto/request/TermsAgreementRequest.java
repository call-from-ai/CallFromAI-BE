package com.example.umcCall.domain.term.dto.request;


import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record TermsAgreementRequest(

        @Schema(
                description = "약관 동의 목록",
                example = """
                        [
                          {
                            "termId": 1,
                            "agreed": true
                          },
                          {
                            "termId": 2,
                            "agreed": true
                          }
                        ]
                        """
        )
        @NotEmpty(message = "약관 동의 목록은 비어 있을 수 없습니다.")
        List<@Valid Agreement> agreements

) {

    public record Agreement(

            @Schema(
                    description = "약관 ID",
                    example = "1"
            )
            @NotNull(message = "약관 ID는 필수입니다.")
            Long termId,

            @Schema(
                    description = "약관 동의 여부",
                    example = "true"
            )
            @NotNull(message = "동의 여부는 필수입니다.")
            Boolean agreed
    ) {
    }
}