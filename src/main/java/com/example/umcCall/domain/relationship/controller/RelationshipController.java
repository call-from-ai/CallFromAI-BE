package com.example.umcCall.domain.relationship.controller;

import com.example.umcCall.domain.relationship.dto.request.ContactPreferenceUpdateRequest;
import com.example.umcCall.domain.relationship.dto.response.ContactPreferenceResponse;
import com.example.umcCall.domain.relationship.dto.response.CurrentRelationshipResponse;
import com.example.umcCall.domain.relationship.service.RelationshipService;
import com.example.umcCall.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "관계", description = "현재 관계 조회 및 설정 API")
@RestController
@RequestMapping("/relationships")
@RequiredArgsConstructor
public class RelationshipController {

    private final RelationshipService relationshipService;

    @Operation(summary = "현재 관계 요약 조회", description = "홈 화면에 표시할 메인 관계와 통화 통계를 조회한다.")
    @GetMapping("/current")
    public ApiResponse<CurrentRelationshipResponse> getCurrentRelationship(
            @AuthenticationPrincipal Long memberId) {
        return ApiResponse.onSuccess(relationshipService.getCurrentRelationship(memberId));
    }

    @Operation(summary = "선호 통화 시간 변경", description = "현재 메인 캐릭터의 선호 통화 시간대를 변경한다.")
    @PatchMapping("/current/contact-preference")
    public ApiResponse<ContactPreferenceResponse> updateContactPreference(
            @AuthenticationPrincipal Long memberId,
            @RequestBody @Valid ContactPreferenceUpdateRequest request) {
        return ApiResponse.onSuccess(
                relationshipService.updateContactPreference(memberId, request.preferTime()));
    }
}
