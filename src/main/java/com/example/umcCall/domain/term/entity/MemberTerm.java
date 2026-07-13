package com.example.umcCall.domain.term.entity;

import com.example.umcCall.domain.member.entity.Member;
import com.example.umcCall.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "member_term",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_member_term_member_term",
                        columnNames = {"member_id", "term_id"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberTerm extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_term_id")
    private Long id;

    @Column(name = "is_agreed", nullable = false)
    private boolean isAgreed;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "term_id", nullable = false)
    private Term term;

    @Builder
    private MemberTerm(boolean isAgreed, Member member, Term term) {
        this.isAgreed = isAgreed;
        this.member = member;
        this.term = term;
    }

    public static MemberTerm createAgreement(Member member, Term term, boolean isAgreed) {
        return MemberTerm.builder()
                .member(member)
                .term(term)
                .isAgreed(isAgreed)
                .build();
    }

    public void updateAgreement(boolean isAgreed) {
        this.isAgreed = isAgreed;
    }
}