package com.example.umcCall.global.error;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

/**
 * Validation 실패 시 필드별 에러 목록을 함께 내려주기 위한 상세 응답.
 * ApiResponse.data 자리에 이 객체를 담아서 반환한다.
 */
@Getter
public class ErrorResponse {

    private final List<FieldErrorDetail> errors;

    private ErrorResponse(List<FieldErrorDetail> errors) {
        this.errors = errors;
    }

    public static ErrorResponse of(BindingResult bindingResult) {
        List<FieldErrorDetail> details = new ArrayList<>();
        for (FieldError fieldError : bindingResult.getFieldErrors()) {
            details.add(FieldErrorDetail.of(
                    fieldError.getField(),
                    fieldError.getRejectedValue() == null ? "" : fieldError.getRejectedValue().toString(),
                    fieldError.getDefaultMessage()
            ));
        }
        return new ErrorResponse(details);
    }

    @Getter
    public static class FieldErrorDetail {
        private final String field;
        private final String value;
        private final String reason;

        private FieldErrorDetail(String field, String value, String reason) {
            this.field = field;
            this.value = value;
            this.reason = reason;
        }

        public static FieldErrorDetail of(String field, String value, String reason) {
            return new FieldErrorDetail(field, value, reason);
        }
    }
}
