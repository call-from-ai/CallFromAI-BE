package com.example.umcCall.domain.member.repository;

import com.example.umcCall.domain.member.entity.Member;
import com.example.umcCall.domain.member.enums.SocialType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findBySocialUidAndSocialType(String socialUid, SocialType socialType);
}