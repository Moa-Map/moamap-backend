package com.moamap.user.inquiry.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moamap.common.exception.CommonErrorCode;
import com.moamap.user.exception.UserErrorCode;
import com.moamap.user.inquiry.dto.InquiryCreateRequest;
import com.moamap.user.inquiry.dto.InquiryDetailResponse;
import com.moamap.user.inquiry.dto.InquirySummaryResponse;
import com.moamap.user.inquiry.entity.InquiryStatus;
import com.moamap.user.inquiry.entity.InquiryType;
import com.moamap.user.user.entity.Role;
import com.moamap.user.user.entity.User;
import com.moamap.user.user.exception.UserNotFoundException;
import com.moamap.user.user.repository.UserRepository;
import com.moamap.user.user.service.AdminGuard;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 문의 등록·관리를 실제 DB로 확인한다. 도배 제한, 개인정보 노출 범위, 관리자 권한 순서를 본다.
 */
@DataJpaTest
@Import({InquiryService.class, InquiryAdminService.class, AdminGuard.class})
class InquiryServiceTest {

    @Autowired private InquiryService inquiryService;
    @Autowired private InquiryAdminService inquiryAdminService;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager em;

    private Long adminId;
    private Long userId;

    @BeforeEach
    void setUp() {
        adminId = user("admin", Role.ADMIN).getId();
        userId = user("member", Role.USER).getId();
    }

    @Test
    void 등록하면_접수_상태로_저장되고_이메일_앞뒤_공백은_지운다() {
        Long inquiryId = inquiryService.create(userId, request(InquiryType.TECHNICAL, "  user@example.com ", "장소가 안 보여요"))
                .inquiryId();

        InquiryDetailResponse saved = inquiryAdminService.getInquiry(adminId, inquiryId);
        assertThat(saved.status()).isEqualTo(InquiryStatus.RECEIVED);
        assertThat(saved.replyEmail()).isEqualTo("user@example.com");
        assertThat(saved.userId()).isEqualTo(userId);
        assertThat(saved.answeredAt()).isNull();
    }

    @Test
    void 최근_24시간에_10건을_넘으면_거부한다() {
        for (int i = 0; i < InquiryService.DAILY_LIMIT; i++) {
            inquiryService.create(userId, request(InquiryType.OTHER, "user@example.com", "문의 " + i));
        }

        assertThatThrownBy(() -> inquiryService.create(userId, request(InquiryType.OTHER, "user@example.com", "11번째")))
                .extracting("errorCode").isEqualTo(UserErrorCode.TOO_MANY_INQUIRIES);
    }

    @Test
    void 하루가_지난_문의는_제한에_세지_않는다() {
        for (int i = 0; i < InquiryService.DAILY_LIMIT; i++) {
            inquiryService.create(userId, request(InquiryType.OTHER, "user@example.com", "문의 " + i));
        }
        em.flush();
        // 등록 시각을 25시간 전으로 돌린다. 시계를 기다리지 않고 경계를 확인하기 위해서다.
        em.createQuery("update Inquiry i set i.createdAt = :past where i.userId = :userId")
                .setParameter("past", Instant.now().minus(25, ChronoUnit.HOURS))
                .setParameter("userId", userId)
                .executeUpdate();

        assertThat(inquiryService.create(userId, request(InquiryType.OTHER, "user@example.com", "다음 날 문의")).inquiryId())
                .isNotNull();
    }

    @Test
    void 제한은_사람마다_따로_센다() {
        for (int i = 0; i < InquiryService.DAILY_LIMIT; i++) {
            inquiryService.create(userId, request(InquiryType.OTHER, "user@example.com", "문의 " + i));
        }
        Long otherId = user("other", Role.USER).getId();

        assertThat(inquiryService.create(otherId, request(InquiryType.OTHER, "other@example.com", "문의")).inquiryId())
                .isNotNull();
    }

    @Test
    void 탈퇴한_회원은_문의할_수_없다() {
        User member = userRepository.findById(userId).orElseThrow();
        member.withdraw(Instant.now());
        userRepository.saveAndFlush(member);

        assertThatThrownBy(() -> inquiryService.create(userId, request(InquiryType.OTHER, "user@example.com", "문의")))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void 관리자_목록은_최신순이고_상태와_유형으로_거를_수_있다() {
        Long first = inquiryService.create(userId, request(InquiryType.TECHNICAL, "user@example.com", "첫 문의")).inquiryId();
        inquiryService.create(userId, request(InquiryType.ACCOUNT, "user@example.com", "둘째 문의"));
        inquiryAdminService.changeStatus(adminId, first, InquiryStatus.ANSWERED);

        Page<InquirySummaryResponse> all = inquiryAdminService.getInquiries(adminId, null, null, PageRequest.of(0, 20));
        Page<InquirySummaryResponse> answered = inquiryAdminService.getInquiries(adminId, InquiryStatus.ANSWERED, null, PageRequest.of(0, 20));
        Page<InquirySummaryResponse> account = inquiryAdminService.getInquiries(adminId, null, InquiryType.ACCOUNT, PageRequest.of(0, 20));

        assertThat(all.getContent()).extracting(InquirySummaryResponse::preview).containsExactly("둘째 문의", "첫 문의");
        assertThat(answered.getContent()).extracting(InquirySummaryResponse::inquiryId).containsExactly(first);
        assertThat(account.getContent()).extracting(InquirySummaryResponse::preview).containsExactly("둘째 문의");
    }

    @Test
    void 목록_미리보기는_50자로_자르고_이모지가_반으로_깨지지_않는다() {
        String content = "😀".repeat(60);
        inquiryService.create(userId, request(InquiryType.OTHER, "user@example.com", content));

        String preview = inquiryAdminService.getInquiries(adminId, null, null, PageRequest.of(0, 20))
                .getContent().get(0).preview();

        assertThat(preview).isEqualTo("😀".repeat(50) + "…");
    }

    @Test
    void 목록_응답에는_이메일_필드가_없다() {
        // 목록에서 개인정보를 한꺼번에 내보내지 않는다. 이메일은 상세에서만 준다.
        assertThat(List.of(InquirySummaryResponse.class.getRecordComponents()))
                .extracting(component -> component.getName())
                .doesNotContain("replyEmail", "content");
    }

    @Test
    void 한_번에_내려주는_개수는_최대_50개다() {
        assertThat(inquiryAdminService.getInquiries(adminId, null, null, PageRequest.of(0, 1000)).getSize())
                .isEqualTo(InquiryAdminService.MAX_PAGE_SIZE);
    }

    @Test
    void 답변_완료로_바꾸면_답변_시각을_남기고_되돌리면_비운다() {
        Long inquiryId = inquiryService.create(userId, request(InquiryType.OTHER, "user@example.com", "문의")).inquiryId();

        InquiryDetailResponse answered = inquiryAdminService.changeStatus(adminId, inquiryId, InquiryStatus.ANSWERED);
        InquiryDetailResponse reopened = inquiryAdminService.changeStatus(adminId, inquiryId, InquiryStatus.IN_PROGRESS);

        assertThat(answered.answeredAt()).isNotNull();
        assertThat(reopened.status()).isEqualTo(InquiryStatus.IN_PROGRESS);
        assertThat(reopened.answeredAt()).isNull();
    }

    @Test
    void 없는_문의는_404다() {
        assertThatThrownBy(() -> inquiryAdminService.getInquiry(adminId, 999_999L))
                .extracting("errorCode").isEqualTo(UserErrorCode.INQUIRY_NOT_FOUND);
    }

    @Test
    void 일반_회원은_문의를_열람할_수_없다() {
        Long inquiryId = inquiryService.create(userId, request(InquiryType.OTHER, "user@example.com", "문의")).inquiryId();

        assertThatThrownBy(() -> inquiryAdminService.getInquiry(userId, inquiryId))
                .extracting("errorCode").isEqualTo(CommonErrorCode.ACCESS_DENIED);
        assertThatThrownBy(() -> inquiryAdminService.getInquiries(userId, null, null, PageRequest.of(0, 20)))
                .extracting("errorCode").isEqualTo(CommonErrorCode.ACCESS_DENIED);
    }

    @Test
    void 관리자가_아니면_없는_문의라도_404가_아니라_403이다() {
        assertThatThrownBy(() -> inquiryAdminService.getInquiry(userId, 999_999L))
                .extracting("errorCode").isEqualTo(CommonErrorCode.ACCESS_DENIED);
    }

    private static InquiryCreateRequest request(InquiryType type, String email, String content) {
        return new InquiryCreateRequest(type, email, content);
    }

    private User user(String providerId, Role role) {
        User user = User.createSocialUser("kakao", providerId, "닉네임", null, null);
        ReflectionTestUtils.setField(user, "role", role);
        return userRepository.saveAndFlush(user);
    }
}
