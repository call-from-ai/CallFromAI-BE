package com.example.umcCall.domain.term.service;

import com.example.umcCall.domain.member.entity.Member;
import com.example.umcCall.domain.member.repository.MemberRepository;
import com.example.umcCall.domain.term.dto.response.TermResponse;
import com.example.umcCall.domain.term.dto.request.TermsAgreementRequest;
import com.example.umcCall.domain.term.entity.MemberTerm;
import com.example.umcCall.domain.term.entity.Term;
import com.example.umcCall.domain.term.repository.MemberTermRepository;
import com.example.umcCall.domain.term.repository.TermRepository;
import com.example.umcCall.global.apiPayload.code.GeneralErrorCode;
import com.example.umcCall.global.exception.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TermService {

    private final TermRepository termRepository;
    private final MemberTermRepository memberTermRepository;
    private final MemberRepository memberRepository;

    public List<TermResponse> getTerms() {
        return termRepository.findAll().stream()
                .map(TermResponse::from)
                .toList();
    }

    @Transactional
    public void agreeTerms(Long memberId, List<TermsAgreementRequest.Agreement> agreements) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BaseException(GeneralErrorCode.MEMBER_NOT_FOUND));

        Map<Long, Boolean> agreementMap = agreements.stream()
                .collect(Collectors.toMap(
                        TermsAgreementRequest.Agreement::termId,
                        TermsAgreementRequest.Agreement::agreed
                ));

        List<Term> allTerms = termRepository.findAll();

        for (Term term : allTerms) {
            boolean isAgreed = agreementMap.getOrDefault(term.getId(), false);

            if (term.isRequired() && !isAgreed) {
                throw new BaseException(GeneralErrorCode.REQUIRED_TERM_NOT_AGREED);
            }
        }

        for (Term term : allTerms) {
            boolean isAgreed = agreementMap.getOrDefault(term.getId(), false);

            MemberTerm memberTerm = memberTermRepository.findByMember_IdAndTerm_Id(memberId, term.getId())
                    .orElseGet(() -> MemberTerm.createAgreement(member, term, isAgreed));

            memberTerm.updateAgreement(isAgreed);
            memberTermRepository.save(memberTerm);
        }
    }
}