package com.example.umcCall.domain.term.repository;

import com.example.umcCall.domain.term.entity.Term;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TermRepository extends JpaRepository<Term, Long> {
}