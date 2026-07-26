package com.example.umcCall.domain.term.repository;

import com.example.umcCall.domain.term.entity.MemberTerm;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MemberTermRepository extends JpaRepository<MemberTerm, Long> {
    Optional<MemberTerm> findByMember_IdAndTerm_Id(Long memberId, Long termId);
    List<MemberTerm> findByMember_Id(Long memberId);
}