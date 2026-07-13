package com.example.umcCall.domain.term.controller;

import com.example.umcCall.domain.term.dto.response.TermResponse;
import com.example.umcCall.domain.term.dto.request.TermsAgreementRequest;
import com.example.umcCall.domain.term.service.TermService;
import com.example.umcCall.global.apiPayload.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class TermController {

    private final TermService termService;

    @GetMapping("/terms")
    public ResponseEntity<ApiResponse<List<TermResponse>>> getTerms() {
        return ResponseEntity.ok(ApiResponse.onSuccess(termService.getTerms()));
    }

    @PostMapping("/members/me/terms")
    public ResponseEntity<ApiResponse<Void>> agreeTerms(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody TermsAgreementRequest request
    ) {
        termService.agreeTerms(memberId, request.agreements());
        return ResponseEntity.ok(ApiResponse.onSuccess());
    }
}