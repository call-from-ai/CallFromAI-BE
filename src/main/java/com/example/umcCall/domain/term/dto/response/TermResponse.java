package com.example.umcCall.domain.term.dto.response;

import com.example.umcCall.domain.term.entity.Term;

public record TermResponse(
        Long termId,
        String title,
        String content,
        boolean isRequired
) {
    public static TermResponse from(Term term) {
        return new TermResponse(term.getId(), term.getTitle(), term.getContent(), term.isRequired());
    }
}