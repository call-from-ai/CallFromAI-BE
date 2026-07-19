package com.example.umcCall.domain.character.entity;

import com.example.umcCall.domain.image.enums.Gender;
import com.example.umcCall.domain.character.enums.Job;
import com.example.umcCall.domain.character.enums.PreferTime;
import com.example.umcCall.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * 온보딩에서 생성하는 캐릭터(이상형) 엔티티.
 * "character"가 MySQL 예약어라 백틱으로 감싸서 DDL 충돌을 피한다.
 */
@Entity
@Getter
@Table(name = "`character`")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Character extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "character_id")
    private Long id;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "first_name")
    private String firstName;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    private Integer age;

    @Enumerated(EnumType.STRING)
    private Job job;

    @Enumerated(EnumType.STRING)
    private PreferTime preferTime;

    private String mbti;

    @Column(name = "image_url", length = 2048)
    private String imageUrl;

    // 정보 수정 1회 제한
    @Column(name = "is_edited")
    private boolean edited;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    public Character(String lastName, String firstName, Gender gender, Integer age, Job job,
                      PreferTime preferTime, String mbti, String imageUrl) {
        this.lastName = lastName;
        this.firstName = firstName;
        this.gender = gender;
        this.age = age;
        this.job = job;
        this.preferTime = preferTime;
        this.mbti = mbti;
        this.edited = false;
        this.imageUrl = imageUrl;
    }

    public String getName() {
        return lastName + firstName;
    }

    public void markDeleted() {
        this.deletedAt = LocalDateTime.now();
    }
}
