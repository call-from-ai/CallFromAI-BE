package com.example.umcCall.domain.member.controller;

import com.example.umcCall.domain.member.dto.response.MemberResponse;
import com.example.umcCall.domain.member.dto.request.MemberUpdateRequest;
import com.example.umcCall.domain.member.service.MemberService;
import com.example.umcCall.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "회원", description = "내 정보 조회/수정/탈퇴 API")
@RestController
@RequestMapping("/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @Operation(
            summary = "내 정보 조회",
            description = """
                    이름, 프로필 이미지, 성별, 생년월일, MBTI, 직업,
                    온보딩 여부, 소셜 로그인 유형 및 통화권 잔액을 반환한다.
                    """
    )
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MemberResponse>> getMyInfo(@AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(ApiResponse.onSuccess(memberService.getMyInfo(memberId)));
    }

    @Operation(
            summary = "내 정보 수정",
            description = """
                    인증된 회원의 프로필 정보를 수정한다.
                    요청에 포함된 이름, 프로필 이미지, 성별, 생년월일, MBTI, 직업의 정보를 갱신한다.
                    """
    )
    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<MemberResponse>> updateMyInfo(
            @AuthenticationPrincipal Long memberId,
            @RequestBody MemberUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.onSuccess(memberService.updateMyInfo(memberId, request)));
    }

    @Operation(
            summary = "회원 탈퇴",
            description = """
                    인증된 회원의 계정을 탈퇴 처리하고 회원 상태를 비활성화한다.
                    탈퇴 이후 기존 토큰을 통한 서비스 이용이 제한된다.
                    """
    )
    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<Void>> withdraw(@AuthenticationPrincipal Long memberId) {
        memberService.withdraw(memberId);
        return ResponseEntity.ok(ApiResponse.onSuccess());
    }
}
