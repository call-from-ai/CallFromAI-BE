package com.example.umcCall.domain.image.repository;

import com.example.umcCall.domain.image.entity.PresetImage;
import com.example.umcCall.domain.image.enums.Gender;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PresetImageRepository extends JpaRepository<PresetImage, Long> {
    List<PresetImage> findByGender(Gender gender);
    boolean existsByGenderAndImageUrl(Gender gender, String imageUrl);
}