package com.example.umcCall.domain.image.controller;

import com.example.umcCall.domain.image.dto.response.PresetImageResponse;
import com.example.umcCall.domain.image.enums.Gender;
import com.example.umcCall.domain.image.service.PresetImageService;
import com.example.umcCall.global.apiPayload.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class PresetImageController {

    private final PresetImageService presetImageService;

    @GetMapping("/preset-images")
    public ApiResponse<List<PresetImageResponse>> getPresetImages(@RequestParam Gender gender) {
        return ApiResponse.onSuccess(presetImageService.getPresetImages(gender));
    }
}