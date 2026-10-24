package com.moamap.user.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moamap.common.exception.BusinessException;
import com.moamap.common.exception.CommonErrorCode;
import com.moamap.user.event.ReportReceivedEvent;
import com.moamap.user.exception.UserErrorCode;
import com.moamap.user.outbox.OutboxEvent;
import com.moamap.user.outbox.OutboxEventRepository;
import com.moamap.user.outbox.OutboxRecorder;
import com.moamap.user.report.dto.ReportRequest;
import com.moamap.user.report.dto.UserReportAdminResponse;
import com.moamap.user.report.entity.ReportReason;
import com.moamap.user.report.repository.UserReportRepository;
import com.moamap.user.report.service.UserReportAdminService;
import com.moamap.user.report.service.UserReportService;
import com.moamap.user.user.entity.Role;
import com.moamap.user.user.entity.User;
import com.moamap.user.user.repository.UserRepository;
import com.moamap.user.user.service.AdminGuard;
import java.time.Instant;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 사용자 신고와 운영자 확인을 실제 DB로 확인한다. 운영자 알림은 outbox 기록으로 본다.
 */
@DataJpaTest
@Import({UserReportService.class, UserReportAdminService.class, AdminGuard.class, OutboxRecorder.class,
        JacksonAutoConfiguration.class})
class UserReportServiceTest {

    @Autowired private UserReportService userReportService;
    @Autowired private UserReportAdminService userReportAdminService;
    @Autowired private UserReportRepository userReportRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private OutboxEventRepository outboxEventRepository;

    private Long reporterId;
    private Long targetId;
    private Long adminId;
    private Long secondAdminId;

    @BeforeEach
    void setUp() {
        reporterId = user("reporter", Role.USER).getId();
        targetId = user("target", Role.USER).getId();
        adminId = user("admin", Role.ADMIN).getId();
        secondAdminId = user("admin2", Role.ADMIN).getId();
    }

    @Test
    void 신고하면_저장되고_운영자마다_알림_이벤트를_남긴다() {
        Long reportId = userReportService.report(reporterId, targetId, request(ReportReason.ABUSE, null)).reportId();

        assertThat(userReportRepository.findById(reportId)).hasValueSatisfying(report -> {
            assertThat(report.getTargetUserId()).isEqualTo(targetId);
            assertThat(report.getReporterId()).isEqualTo(reporterId);
            assertThat(report.getReason()).isEqualTo(ReportReason.ABUSE);
        });
        List<OutboxEvent> events = outboxEventRepository.findAll();
        assertThat(events).hasSize(2).allSatisfy(event -> {
            assertThat(event.getEventType()).isEqualTo(ReportReceivedEvent.TYPE);
            assertThat(event.getPayload()).contains("\"targetId\":" + reportId);
            // 알림에는 신고자를 싣지 않는다.
            assertThat(event.getPayload()).doesNotContain("actorId");
        });
        assertThat(events).extracting(OutboxEvent::getPayload)
                .anyMatch(payload -> payload.contains("\"recipientId\":" + adminId))
                .anyMatch(payload -> payload.contains("\"recipientId\":" + secondAdminId));
    }

    @Test
    void 자기_자신은_신고할_수_없다() {
        assertErrorCode(() -> userReportService.report(reporterId, reporterId, request(ReportReason.SPAM, null)),
                UserErrorCode.CANNOT_REPORT_SELF);
    }

    @Test
    void 같은_사용자를_두_번_신고할_수_없다() {
        userReportService.report(reporterId, targetId, request(ReportReason.SPAM, null));

        assertErrorCode(() -> userReportService.report(reporterId, targetId, request(ReportReason.ABUSE, null)),
                UserErrorCode.ALREADY_REPORTED_USER);
        assertThat(userReportRepository.findAll()).hasSize(1);
    }

    @Test
    void 없거나_탈퇴한_사용자는_신고할_수_없다() {
        User withdrawn = userRepository.findById(targetId).orElseThrow();
        withdrawn.withdraw(Instant.now());
        userRepository.flush();

        assertErrorCode(() -> userReportService.report(reporterId, targetId, request(ReportReason.SPAM, null)),
                UserErrorCode.USER_NOT_FOUND);
        assertErrorCode(() -> userReportService.report(reporterId, 9999L, request(ReportReason.SPAM, null)),
                UserErrorCode.USER_NOT_FOUND);
    }

    @Test
    void 운영자는_신고와_신고된_프로필_내용_누적_신고_수를_함께_본다() {
        Long otherReporter = user("other", Role.USER).getId();
        userReportService.report(reporterId, targetId, request(ReportReason.ABUSE, null));
        userReportService.report(otherReporter, targetId, request(ReportReason.OTHER, "프로필 사진이 부적절해요"));

        List<UserReportAdminResponse> reports = userReportAdminService.getReports(adminId, PageRequest.of(0, 20)).getContent();

        assertThat(reports).hasSize(2);
        // 최신순
        assertThat(reports.get(0).reporterId()).isEqualTo(otherReporter);
        assertThat(reports.get(0).detail()).isEqualTo("프로필 사진이 부적절해요");
        assertThat(reports).allSatisfy(report -> {
            assertThat(report.targetReportCount()).isEqualTo(2);
            assertThat(report.target().userId()).isEqualTo(targetId);
            assertThat(report.target().nickname()).isEqualTo("닉네임");
            assertThat(report.target().withdrawn()).isFalse();
        });
    }

    @Test
    void 운영자가_아니면_신고_목록을_볼_수_없다() {
        assertThatThrownBy(() -> userReportAdminService.getReports(reporterId, PageRequest.of(0, 20)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(CommonErrorCode.ACCESS_DENIED);
    }

    private User user(String providerId, Role role) {
        User user = User.createSocialUser("kakao", providerId, "닉네임", null, null);
        ReflectionTestUtils.setField(user, "role", role);
        return userRepository.saveAndFlush(user);
    }

    private static ReportRequest request(ReportReason reason, String detail) {
        return new ReportRequest(reason, detail);
    }

    private static void assertErrorCode(ThrowingCallable call, UserErrorCode code) {
        assertThatThrownBy(call).isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo(code);
    }
}
