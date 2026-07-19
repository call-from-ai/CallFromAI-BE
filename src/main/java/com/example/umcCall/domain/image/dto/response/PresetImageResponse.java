package com.example.umcCall.domain.image.dto.response;

import com.example.umcCall.domain.image.entity.PresetImage;

public record PresetImageResponse(
        Long presetImageId,
        String imageUrl
) {
    public static PresetImageResponse from(PresetImage image) {
        return new PresetImageResponse(image.getId(), image.getImageUrl());
    }
}