package com.moamap.user.term.repository;

import com.moamap.user.term.entity.Term;
import com.moamap.user.term.entity.TermKind;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TermRepository extends JpaRepository<Term, Long> {

    /** 앱에 보여줄 약관. 화면 순서대로, 같은 순서면 먼저 등록된 것이 앞이다. */
    List<Term> findByActiveTrueOrderByDisplayOrderAscIdAsc();

    List<Term> findByActiveTrueAndKindOrderByDisplayOrderAscIdAsc(TermKind kind);
}
