package com.moamap.user.term.service;

import com.moamap.common.exception.BusinessException;
import com.moamap.user.exception.UserErrorCode;
import com.moamap.user.term.dto.TermAgreementRequest;
import com.moamap.user.term.dto.TermAgreementStatusResponse;
import com.moamap.user.term.dto.TermSummaryResponse;
import com.moamap.user.term.entity.Term;
import com.moamap.user.term.entity.TermKind;
import com.moamap.user.term.entity.UserTerm;
import com.moamap.user.term.repository.TermRepository;
import com.moamap.user.term.repository.UserTermRepository;
import com.moamap.user.user.entity.User;
import com.moamap.user.user.repository.UserRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 약관 동의.
 *
 * 판단 규칙은 하나다: 켜져 있는 필수 약관 중 내 동의 기록이 없는 것이 있으면 동의가 필요하다.
 * 약관을 개정하면 새 줄이 생기고 이전 줄이 꺼지므로, 바뀐 약관만 다시 동의 대상이 된다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TermAgreementService {

    private final TermRepository termRepository;
    private final UserTermRepository userTermRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public TermAgreementStatusResponse getMyStatus(Long userId) {
        requireActiveUser(userRepository.findById(userId));
        return TermAgreementStatusResponse.of(pendingTerms(userId));
    }

    /**
     * 같은 회원의 요청이 동시에 와도 기록이 두 번 쌓이지 않도록 회원 행을 잠그고 처리한다.
     * 이미 동의한 약관은 다시 저장하지 않아, 같은 요청을 다시 보내도 안전하다.
     */
    public TermAgreementStatusResponse agree(Long userId, TermAgreementRequest request) {
        requireActiveUser(userRepository.findByIdForUpdate(userId));

        Set<Long> requestedIds = new LinkedHashSet<>(request.termIds());
        List<Term> requested = termRepository.findAllById(requestedIds);
        if (requested.size() != requestedIds.size()) {
            throw new BusinessException(UserErrorCode.INVALID_TERM_AGREEMENT);
        }
        // 지난 버전에 동의하려는 요청은 "그사이 약관이 바뀌었다"로 알려, 앱이 화면을 새로 그리게 한다.
        if (requested.stream().anyMatch(term -> !term.isActive())) {
            throw new BusinessException(UserErrorCode.TERM_OUTDATED);
        }
        if (requested.stream().anyMatch(term -> !term.isRequired())) {
            throw new BusinessException(UserErrorCode.INVALID_TERM_AGREEMENT);
        }
        List<TermSummaryResponse> pending = pendingTerms(userId);
        if (!pending.stream().map(TermSummaryResponse::termId).allMatch(requestedIds::contains)) {
            throw new BusinessException(UserErrorCode.REQUIRED_TERMS_NOT_AGREED);
        }

        Set<Long> agreedIds = userTermRepository.findTermIdsByUserId(userId);
        userTermRepository.saveAll(requestedIds.stream()
                .filter(termId -> !agreedIds.contains(termId))
                .map(termId -> UserTerm.agree(userId, termId))
                .toList());
        return TermAgreementStatusResponse.of(List.of());
    }

    private List<TermSummaryResponse> pendingTerms(Long userId) {
        Set<Long> agreedIds = userTermRepository.findTermIdsByUserId(userId);
        return termRepository.findByActiveTrueAndKindOrderByDisplayOrderAscIdAsc(TermKind.REQUIRED).stream()
                .filter(term -> !agreedIds.contains(term.getId()))
                .map(TermSummaryResponse::from)
                .toList();
    }

    /** 탈퇴한 회원의 남은 액세스 토큰으로 동의 기록이 다시 생기지 않게 한다. */
    private static void requireActiveUser(Optional<User> user) {
        user.filter(found -> !found.isWithdrawn())
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
    }
}
