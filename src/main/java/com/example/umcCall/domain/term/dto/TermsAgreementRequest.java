package com.example.umcCall.domain.term.dto;


import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record TermsAgreementRequest(

        @NotEmpty(message = "약관 동의 목록은 비어 있을 수 없습니다.")
        List<@Valid Agreement> agreements

) {

    public record Agreement(

            @NotNull(message = "약관 ID는 필수입니다.")
            Long termId,

            @NotNull(message = "동의 여부는 필수입니다.")
            Boolean agreed
    ) {
    }
}