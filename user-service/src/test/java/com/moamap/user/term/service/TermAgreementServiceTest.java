package com.moamap.user.term.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moamap.common.exception.BusinessException;
import com.moamap.user.exception.UserErrorCode;
import com.moamap.user.term.catalog.TermContentLoader;
import com.moamap.user.term.dto.TermAgreementRequest;
import com.moamap.user.term.dto.TermAgreementStatusResponse;
import com.moamap.user.term.dto.TermDetailResponse;
import com.moamap.user.term.dto.TermSummaryResponse;
import com.moamap.user.term.entity.Term;
import com.moamap.user.term.entity.TermKind;
import com.moamap.user.term.entity.UserTerm;
import com.moamap.user.term.repository.TermRepository;
import com.moamap.user.term.repository.UserTermRepository;
import com.moamap.user.user.entity.User;
import com.moamap.user.user.repository.UserRepository;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

/**
 * 약관 조회와 동의 규칙을 실제 DB로 확인한다. 본문은 레포의 실제 약관 파일(terms/*.md)을 읽는다.
 */
@DataJpaTest
@Import({TermService.class, TermAgreementService.class, TermContentLoader.class})
class TermAgreementServiceTest {

    @Autowired private TermService termService;
    @Autowired private TermAgreementService termAgreementService;
    @Autowired private TermRepository termRepository;
    @Autowired private UserTermRepository userTermRepository;
    @Autowired private UserRepository userRepository;

    private Long userId;
    private Term serviceV1;
    private Term ageV1;
    private Term policyV1;

    @BeforeEach
    void setUp() {
        userId = userRepository.save(User.createSocialUser("kakao", "member", "닉네임", null, null)).getId();
        serviceV1 = term("SERVICE", "v1", TermKind.REQUIRED, true, "service-v1.md", 1);
        ageV1 = term("AGE", "v1", TermKind.REQUIRED, true, "age-v1.md", 2);
        policyV1 = term("POLICY", "v1", TermKind.VIEW_ONLY, true, "policy-v1.md", 3);
    }

    @Test
    void 약관_목록은_켜진_약관만_화면_순서대로_준다() {
        term("OLD", "v1", TermKind.REQUIRED, false, "service-v1.md", 0);

        assertThat(termService.getActiveTerms()).extracting(TermSummaryResponse::code)
                .containsExactly("SERVICE", "AGE", "POLICY");
    }

    @Test
    void 전문은_Markdown_본문을_준다() {
        TermDetailResponse detail = termService.getTerm(serviceV1.getId());

        assertThat(detail.content()).startsWith("# 서비스 이용약관");
        assertThat(detail.active()).isTrue();
    }

    @Test
    void 지난_버전도_전문을_볼_수_있다() {
        Term old = term("OLD", "v1", TermKind.REQUIRED, false, "service-v1.md", 0);

        assertThat(termService.getTerm(old.getId()).active()).isFalse();
    }

    @Test
    void 없는_약관이나_본문_파일이_없는_약관은_404다() {
        Term broken = term("BROKEN", "v1", TermKind.REQUIRED, false, "missing-v1.md", 0);

        assertErrorCode(() -> termService.getTerm(9999L), UserErrorCode.TERM_NOT_FOUND);
        assertErrorCode(() -> termService.getTerm(broken.getId()), UserErrorCode.TERM_NOT_FOUND);
    }

    @Test
    void 처음에는_필수_약관_전체가_동의_대상이고_보기_전용은_빠진다() {
        TermAgreementStatusResponse status = termAgreementService.getMyStatus(userId);

        assertThat(status.needsAgreement()).isTrue();
        assertThat(status.pendingTerms()).extracting(TermSummaryResponse::termId)
                .containsExactly(serviceV1.getId(), ageV1.getId());
    }

    @Test
    void 필수_약관에_모두_동의하면_더_이상_동의할_것이_없다() {
        TermAgreementStatusResponse response = termAgreementService.agree(userId, request(serviceV1, ageV1));

        assertThat(response.needsAgreement()).isFalse();
        assertThat(termAgreementService.getMyStatus(userId).needsAgreement()).isFalse();
        assertThat(userTermRepository.findTermIdsByUserId(userId))
                .containsExactlyInAnyOrder(serviceV1.getId(), ageV1.getId());
    }

    @Test
    void 필수_약관을_하나라도_빠뜨리면_400이고_아무것도_저장하지_않는다() {
        assertErrorCode(() -> termAgreementService.agree(userId, request(serviceV1)), UserErrorCode.REQUIRED_TERMS_NOT_AGREED);
        assertThat(userTermRepository.findAll()).isEmpty();
    }

    @Test
    void 보기_전용_약관이나_없는_약관에는_동의할_수_없다() {
        assertErrorCode(() -> termAgreementService.agree(userId, request(serviceV1, ageV1, policyV1)),
                UserErrorCode.INVALID_TERM_AGREEMENT);
        assertErrorCode(() -> termAgreementService.agree(userId, new TermAgreementRequest(List.of(serviceV1.getId(), 9999L))),
                UserErrorCode.INVALID_TERM_AGREEMENT);
    }

    @Test
    void 같은_요청을_다시_보내도_기록이_두_번_쌓이지_않는다() {
        termAgreementService.agree(userId, request(serviceV1, ageV1));
        termAgreementService.agree(userId, request(serviceV1, ageV1));

        assertThat(userTermRepository.findAll()).hasSize(2);
    }

    @Test
    void 약관이_개정되면_바뀐_약관만_다시_동의_대상이_된다() {
        termAgreementService.agree(userId, request(serviceV1, ageV1));

        Term serviceV2 = revise(serviceV1, "v2");

        TermAgreementStatusResponse status = termAgreementService.getMyStatus(userId);
        assertThat(status.needsAgreement()).isTrue();
        assertThat(status.pendingTerms()).extracting(TermSummaryResponse::termId).containsExactly(serviceV2.getId());

        termAgreementService.agree(userId, request(serviceV2));
        assertThat(termAgreementService.getMyStatus(userId).needsAgreement()).isFalse();
    }

    @Test
    void 동의_화면을_띄운_사이에_약관이_개정되면_지난_버전_동의는_409다() {
        revise(serviceV1, "v2");

        assertErrorCode(() -> termAgreementService.agree(userId, request(serviceV1, ageV1)), UserErrorCode.TERM_OUTDATED);
        assertThat(userTermRepository.findAll()).isEmpty();
    }

    @Test
    void 지난_버전에_동의했던_기록은_남는다() {
        termAgreementService.agree(userId, request(serviceV1, ageV1));
        revise(serviceV1, "v2");

        assertThat(userTermRepository.findTermIdsByUserId(userId)).contains(serviceV1.getId());
    }

    @Test
    void 필수_약관이_하나도_없으면_동의할_것이_없다() {
        termRepository.findAll().forEach(Term::deactivate);
        termRepository.flush();

        assertThat(termAgreementService.getMyStatus(userId).needsAgreement()).isFalse();
    }

    @Test
    void 탈퇴한_회원은_동의_현황을_보거나_동의할_수_없다() {
        userRepository.findById(userId).orElseThrow().withdraw(Instant.now());
        userRepository.flush();

        assertErrorCode(() -> termAgreementService.getMyStatus(userId), UserErrorCode.USER_NOT_FOUND);
        assertErrorCode(() -> termAgreementService.agree(userId, request(serviceV1, ageV1)), UserErrorCode.USER_NOT_FOUND);
    }

    @Test
    void 남의_동의는_내_동의_현황에_영향을_주지_않는다() {
        Long otherId = userRepository.save(User.createSocialUser("kakao", "other", "다른 사람", null, null)).getId();
        userTermRepository.save(UserTerm.agree(otherId, serviceV1.getId()));

        assertThat(termAgreementService.getMyStatus(userId).pendingTerms()).hasSize(2);
    }

    private Term revise(Term current, String version) {
        current.deactivate();
        return term(current.getCode(), version, current.getKind(), true, current.getContentFile(), current.getDisplayOrder());
    }

    private Term term(String code, String version, TermKind kind, boolean active, String file, int order) {
        return termRepository.saveAndFlush(Term.register(code, code + " 제목", version, kind, active, file, order));
    }

    private static TermAgreementRequest request(Term... terms) {
        return new TermAgreementRequest(Arrays.stream(terms).map(Term::getId).toList());
    }

    private static void assertErrorCode(ThrowingCallable call, UserErrorCode code) {
        assertThatThrownBy(call).isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo(code);
    }
}
