package com.example.umcCall.domain.term.controller;

import com.example.umcCall.domain.term.dto.response.TermResponse;
import com.example.umcCall.domain.term.dto.request.TermsAgreementRequest;
import com.example.umcCall.domain.term.service.TermService;
import com.example.umcCall.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "약관", description = "서비스 약관 조회/동의 처리 API")
@RestController
@RequiredArgsConstructor
public class TermController {

    private final TermService termService;

    @Operation(
            summary = "약관 목록 조회",
            description = """
                    서비스에서 제공하는 전체 약관 목록을 조회한다.
                    각 약관의 ID, 제목, 내용, 필수 동의 여부를 반환한다.
                    """
    )
    @GetMapping("/terms")
    public ResponseEntity<ApiResponse<List<TermResponse>>> getTerms() {
        return ResponseEntity.ok(ApiResponse.onSuccess(termService.getTerms()));
    }

    @Operation(
            summary = "약관 동의 저장",
            description = """
                    인증된 회원의 약관별 동의 여부를 저장한다.
                    필수 약관에 동의하지 않은 경우 요청이 실패한다.
                    """
    )
    @PostMapping("/members/me/terms")
    public ResponseEntity<ApiResponse<Void>> agreeTerms(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody TermsAgreementRequest request
    ) {
        termService.agreeTerms(memberId, request.agreements());
        return ResponseEntity.ok(ApiResponse.onSuccess());
    }
}