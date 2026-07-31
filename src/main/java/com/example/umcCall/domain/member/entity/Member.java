package com.example.umcCall.domain.member.entity;

import com.example.umcCall.domain.image.enums.Gender;
import com.example.umcCall.domain.member.enums.Job;
import com.example.umcCall.domain.member.enums.Mbti;
import com.example.umcCall.domain.member.enums.SocialType;
import com.example.umcCall.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_id")
    private Long id;

    @Column(name = "last_name", length = 5)
    private String lastName;

    @Column(name = "first_name", length = 5)
    private String firstName;

    @Column(name = "image_url", length = 2048)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender")
    private Gender gender;

    @Column(name = "birth")
    private LocalDate birth;

    @Enumerated(EnumType.STRING)
    @Column(name = "mbti")
    private Mbti mbti;

    @Column(name = "social_uid", nullable = false, length = 255)
    private String socialUid;

    @Enumerated(EnumType.STRING)
    @Column(name = "social_type", nullable = false)
    private SocialType socialType;

    @Enumerated(EnumType.STRING)
    @Column(name = "job")
    private Job job;

    @Column(name = "call_ticket_balance", nullable = false)
    private int callTicketBalance;


    @Column(name = "character_created_at")
    private LocalDateTime characterCreatedAt;

    @Column(name = "is_inactive", nullable = false)
    private Boolean isInactive;

    @Column(name = "all_notification_enabled", nullable = false)
    private boolean allNotificationEnabled;

    @Column(name = "night_call_allowed", nullable = false)
    private boolean nightCallAllowed;

    @Column(name = "do_not_disturb_start")
    private LocalTime doNotDisturbStart;

    @Column(name = "do_not_disturb_end")
    private LocalTime doNotDisturbEnd;

    @Builder
    private Member(String socialUid, SocialType socialType) {
        this.socialUid = socialUid;
        this.socialType = socialType;
        this.callTicketBalance = 0;
        this.isInactive = false;
        this.allNotificationEnabled = true;
        this.nightCallAllowed = true;
    }

    public static Member createBySocialLogin(String socialUid, SocialType socialType) {
        return Member.builder()
                .socialUid(socialUid)
                .socialType(socialType)
                .build();
    }

    public boolean isOnboardingCompleted() {
        return this.lastName != null;
    }

    public void updateProfile(String lastName, String firstName, String imageUrl,
                              Gender gender, LocalDate birth, Mbti mbti, Job job) {
        if (lastName != null) this.lastName = lastName;
        if (firstName != null) this.firstName = firstName;
        if (imageUrl != null) this.imageUrl = imageUrl;
        if (gender != null) this.gender = gender;
        if (birth != null) this.birth = birth;
        if (mbti != null) this.mbti = mbti;
        if (job != null) this.job = job;
    }

public void deactivate() {
    this.isInactive = true;
    this.lastName = null;
    this.firstName = null;
    this.profilePhotoUrl = null;
    this.gender = null;
    this.birth = null;
    this.mbti = null;
    this.job = null;
}

    public void markCharacterCreated() {
        this.characterCreatedAt = LocalDateTime.now();
    }

    public void updateNotificationSetting(Boolean allNotificationEnabled, Boolean nightCallAllowed) {
        if (allNotificationEnabled != null) this.allNotificationEnabled = allNotificationEnabled;
        if (nightCallAllowed != null) this.nightCallAllowed = nightCallAllowed;
    }

    public void updateDoNotDisturb(LocalTime start, LocalTime end) {
        this.doNotDisturbStart = start;
        this.doNotDisturbEnd = end;
    }
}

