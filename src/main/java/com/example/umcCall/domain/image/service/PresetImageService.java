package com.example.umcCall.domain.image.service;

import com.example.umcCall.domain.image.dto.response.PresetImageResponse;
import com.example.umcCall.domain.image.enums.Gender;
import com.example.umcCall.domain.image.repository.PresetImageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PresetImageService {

    private final PresetImageRepository presetImageRepository;

    public List<PresetImageResponse> getPresetImages(Gender gender) {
        return presetImageRepository.findByGender(gender).stream()
                .map(PresetImageResponse::from)
                .toList();
    }
}