package com.example.umcCall.domain.ai.controller;

import com.example.umcCall.domain.ai.client.AiServerClient;
import com.example.umcCall.domain.ai.dto.AiHealthResponse;
import com.example.umcCall.global.apiPayload.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("local")
@RequestMapping("/test/ai")
@RequiredArgsConstructor
public class AiDebugController {

    private final AiServerClient aiServerClient;

    @GetMapping("/health")
    public ApiResponse<AiHealthResponse> health() {
        return ApiResponse.onSuccess(aiServerClient.health());
    }
}
