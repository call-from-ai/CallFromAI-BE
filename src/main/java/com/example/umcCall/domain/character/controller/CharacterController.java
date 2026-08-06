package com.example.umcCall.domain.character.controller;

import com.example.umcCall.domain.character.dto.request.CharacterCreateRequest;
import com.example.umcCall.domain.character.dto.request.CharacterUpdateRequest;
import com.example.umcCall.domain.character.dto.response.CharacterCreateResponse;
import com.example.umcCall.domain.character.dto.response.CharacterResponse;
import com.example.umcCall.domain.character.dto.response.CharacterSummaryResponse;
import com.example.umcCall.domain.character.service.CharacterService;
import com.example.umcCall.domain.relationship.dto.response.ChatSummaryResponse;
import com.example.umcCall.domain.relationship.service.ChatSummaryService;
import com.example.umcCall.global.apiPayload.ApiResponse;
import com.example.umcCall.global.apiPayload.code.GeneralSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * 캐릭터 CRUD API.
 */
@Tag(name = "캐릭터", description = "캐릭터(이상형) 생성/조회/활성화/삭제 API")
@RestController
@RequestMapping("/characters")
@RequiredArgsConstructor
public class CharacterController {

    private final CharacterService characterService;
    private final ChatSummaryService chatSummaryService;


    // 캐릭터 생성
    @Operation(summary = "캐릭터 생성", description = "온보딩에서 입력한 정보로 캐릭터를 생성한다. 매력 키워드는 1~5개이며 enum code로 전달한다.")
    @PostMapping
    public ResponseEntity<ApiResponse<CharacterCreateResponse>> createCharacter(
            @AuthenticationPrincipal Long memberId,
            @RequestBody @Valid CharacterCreateRequest request) {
        CharacterCreateResponse response = characterService.createCharacter(memberId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.onSuccess(
                        GeneralSuccessCode.CREATED, response));
    }

    // 캐릭터 정보 수정
    @Operation(summary = "캐릭터 정보 수정", description = "캐릭터 정보를 수정한다. 최초 1회만 가능하다.")
    @PatchMapping("/{characterId}")
    public ApiResponse<Void> updateCharacter(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long characterId,
            @RequestBody @Valid CharacterUpdateRequest request) {
        characterService.updateCharacter(memberId, characterId, request);
        return ApiResponse.onSuccess();
    }

    // 현재 활성 캐릭터 조회
    @Operation(summary = "현재 활성 캐릭터 조회", description = "회원의 현재 활성화된 캐릭터 상세 정보를 반환한다.")
    @GetMapping("/active")
    public ApiResponse<CharacterResponse> getActiveCharacter(Authentication authentication) {
        Long memberId = (Long) authentication.getPrincipal();
        return ApiResponse.onSuccess(characterService.getActiveCharacter(memberId));
    }

    @Operation(summary = "캐릭터 상세 조회", description = "characterId로 특정 캐릭터의 상세 정보를 조회한다. 수정 화면 진입 시 사용한다.")
    @GetMapping("/{characterId}")
    public ApiResponse<CharacterResponse> getCharacter(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long characterId) {
        return ApiResponse.onSuccess(characterService.getCharacter(memberId, characterId));
    }

    // 내 캐릭터 목록 조회
    @Operation(summary = "내 캐릭터 목록 조회", description = "회원이 생성한 캐릭터 목록을 반환한다. 최대 5개라 페이지네이션은 없다.")
    @GetMapping
    public ApiResponse<List<CharacterSummaryResponse>> getMyCharacters(Authentication authentication) {
        Long memberId = (Long) authentication.getPrincipal();
        return ApiResponse.onSuccess(characterService.getMyCharacters(memberId));
    }

    @Operation(
            summary = "AI 대화 요약 조회",
            description = "해당 캐릭터와 사용자가 나눈 대화를 200자 이내로 요약한다.")
    @GetMapping("/{characterId}/chat-summary")
    public ApiResponse<ChatSummaryResponse> getChatSummary(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long characterId) {
        return ApiResponse.onSuccess(chatSummaryService.getSummary(memberId, characterId));
    }

    // 활성 캐릭터 변경
    @Operation(summary = "활성 캐릭터 변경", description = "지정한 캐릭터를 활성 상태로 전환하고 기존 활성 캐릭터는 해제한다.")
    @PatchMapping("/{characterId}/activate")
    public ApiResponse<Void> activateCharacter(
            Authentication authentication, @PathVariable Long characterId) {
        Long memberId = (Long) authentication.getPrincipal();
        characterService.activateCharacter(memberId, characterId);
        return ApiResponse.onSuccess();
    }

    // 캐릭터 삭제
    @Operation(summary = "캐릭터 삭제", description = "본인 소유 캐릭터를 논리 삭제하고 AI 서버 정리 작업을 등록한다.")
    @DeleteMapping("/{characterId}")
    public ApiResponse<Void> deleteCharacter(
            Authentication authentication, @PathVariable Long characterId) {
        Long memberId = (Long) authentication.getPrincipal();
        characterService.deleteCharacter(memberId, characterId);
        return ApiResponse.onSuccess();
    }
}
