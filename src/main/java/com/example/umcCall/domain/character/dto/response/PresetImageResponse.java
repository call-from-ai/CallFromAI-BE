package com.example.umcCall.domain.character.dto.response;

import lombok.Builder;
import lombok.Getter;

/**
 * 프리셋 이미지 조회 응답.
 */
@Getter
@Builder
public class PresetImageResponse {

    private String imageUrl;
}