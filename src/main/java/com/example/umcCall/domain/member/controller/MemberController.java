package com.example.umcCall.domain.member.controller;

import com.example.umcCall.domain.member.dto.response.MemberResponse;
import com.example.umcCall.domain.member.dto.request.MemberUpdateRequest;
import com.example.umcCall.domain.member.service.MemberService;
import com.example.umcCall.global.apiPayload.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MemberResponse>> getMyInfo(@AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(ApiResponse.onSuccess(memberService.getMyInfo(memberId)));
    }

    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<MemberResponse>> updateMyInfo(
            @AuthenticationPrincipal Long memberId,
            @RequestBody MemberUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.onSuccess(memberService.updateMyInfo(memberId, request)));
    }

    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<Void>> withdraw(@AuthenticationPrincipal Long memberId) {
        memberService.withdraw(memberId);
        return ResponseEntity.ok(ApiResponse.onSuccess());
    }
}
