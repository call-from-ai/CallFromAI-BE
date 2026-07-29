package com.example.umcCall.domain.image.controller;

import com.example.umcCall.domain.image.dto.response.PresetImageResponse;
import com.example.umcCall.domain.image.enums.Gender;
import com.example.umcCall.domain.image.service.PresetImageService;
import com.example.umcCall.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "프리셋 이미지", description = "캐릭터/회원 프로필 사진 선택에 공용으로 쓰이는 프리셋 이미지 조회 API")
@RestController
@RequiredArgsConstructor
public class PresetImageController {

    private final PresetImageService presetImageService;

    @Operation(
            summary = "프리셋 이미지 목록 조회",
            description = """
                    성별에 맞는 기본 프로필 이미지 URL 목록을 반환한다.
                    캐릭터 생성/수정, 회원 프로필 사진 선택 화면에서 공용으로 사용된다.
                    """
    )
    @GetMapping("/preset-images")
    public ApiResponse<List<PresetImageResponse>> getPresetImages(
            @Parameter(description = "조회할 성별 (MALE 또는 FEMALE)", required = true)
            @RequestParam Gender gender
    ) {
        return ApiResponse.onSuccess(presetImageService.getPresetImages(gender));
    }
}