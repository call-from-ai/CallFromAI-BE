package com.example.umcCall.domain.image.entity;

import com.example.umcCall.domain.image.enums.Gender;
import com.example.umcCall.domain.image.enums.TTSVoice;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "preset_image")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PresetImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "preset_image_id")
    private Long id;

    @Column(name = "image_url", nullable = false, length = 255)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", nullable = false)
    private Gender gender;

    /**
     * 이 이미지를 고른 캐릭터가 통화에서 낼 목소리.
     * <p>⚠ {@code nullable = false}인 건 <b>프리셋을 추가할 때 목소리 누락을 구조적으로 막기 위해서다</b> —
     * DB {@code DEFAULT}를 걸면 안 넣어도 통과해 이 방어가 그대로 사라진다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "voice", nullable = false, length = 20)
    private TTSVoice voice;
}