package com.example.umcCall.domain.member.entity;

import com.example.umcCall.domain.member.enums.Gender;
import com.example.umcCall.domain.member.enums.Job;
import com.example.umcCall.domain.member.enums.Mbti;
import com.example.umcCall.domain.member.enums.SocialType;
import com.example.umcCall.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

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

    @Column(name = "profile_photo_url", length = 255)
    private String profilePhotoUrl;

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

    @CreatedDate
    @Column(name = "character_created_at", nullable = false)
    private LocalDateTime charcterCreatedAt;

    @Column(name = "is_inactive", nullable = false)
    private Boolean isInactive;

    @Builder
    private Member(String socialUid, SocialType socialType) {
        this.socialUid = socialUid;
        this.socialType = socialType;
        this.callTicketBalance = 0;
        this.isInactive = false;
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

    public void completeOnboarding(String lastName, String firstName, String profilePhotoUrl,
                                   Gender gender, LocalDate birth, Mbti mbti, Job job) {
        this.lastName = lastName;
        this.firstName = firstName;
        this.profilePhotoUrl = profilePhotoUrl;
        this.gender = gender;
        this.birth = birth;
        this.mbti = mbti;
        this.job = job;
    }
}