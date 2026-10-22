package com.moamap.user.term.service;

import com.moamap.common.exception.BusinessException;
import com.moamap.user.exception.UserErrorCode;
import com.moamap.user.term.catalog.TermContentLoader;
import com.moamap.user.term.dto.TermDetailResponse;
import com.moamap.user.term.dto.TermSummaryResponse;
import com.moamap.user.term.entity.Term;
import com.moamap.user.term.repository.TermRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 약관 조회. 로그인 없이 볼 수 있다(가입 전 화면에서 쓴다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TermService {

    private final TermRepository termRepository;
    private final TermContentLoader contentLoader;

    public List<TermSummaryResponse> getActiveTerms() {
        return termRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc().stream()
                .map(TermSummaryResponse::from)
                .toList();
    }

    /** 지난 버전도 볼 수 있다. 동의 기록이 가리키는 "그때의 문구"를 확인하는 데 쓴다. */
    public TermDetailResponse getTerm(Long termId) {
        Term term = termRepository.findById(termId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.TERM_NOT_FOUND));
        return TermDetailResponse.of(term, loadContent(term));
    }

    private String loadContent(Term term) {
        try {
            return contentLoader.load(term.getContentFile());
        } catch (IllegalStateException e) {
            // terms.yml에서 빠지면서 파일까지 지운 옛 버전. 서버 오류가 아니라 "없는 약관"으로 답한다.
            log.warn("약관 본문 파일을 읽지 못했다. termId={}", term.getId(), e);
            throw new BusinessException(UserErrorCode.TERM_NOT_FOUND);
        }
    }
}
