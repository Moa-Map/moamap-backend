package com.moamap.user.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moamap.common.exception.BusinessException;
import com.moamap.user.block.dto.BlockedUserResponse;
import com.moamap.user.block.repository.UserBlockRepository;
import com.moamap.user.block.service.UserBlockService;
import com.moamap.user.event.ReportReceivedEvent;
import com.moamap.user.exception.UserErrorCode;
import com.moamap.user.outbox.OutboxEventRepository;
import com.moamap.user.outbox.OutboxRecorder;
import com.moamap.user.report.dto.ReportRequest;
import com.moamap.user.report.entity.ReportReason;
import com.moamap.user.report.entity.UserReport;
import com.moamap.user.report.repository.UserReportRepository;
import com.moamap.user.report.service.UserReportService;
import com.moamap.user.user.entity.Role;
import com.moamap.user.user.entity.User;
import com.moamap.user.user.repository.UserRepository;
import java.time.Instant;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 차단을 실제 DB로 확인한다. 차단하면 운영자 통보(사유 BLOCKED 신고 + 알림 outbox)가 함께 남아야 한다.
 */
@DataJpaTest
@Import({UserBlockService.class, UserReportService.class, OutboxRecorder.class, JacksonAutoConfiguration.class})
class UserBlockServiceTest {

    @Autowired private UserBlockService userBlockService;
    @Autowired private UserReportService userReportService;
    @Autowired private UserBlockRepository userBlockRepository;
    @Autowired private UserReportRepository userReportRepository;
    @Autowired private OutboxEventRepository outboxEventRepository;
    @Autowired private UserRepository userRepository;

    private Long me;
    private Long target;

    @BeforeEach
    void setUp() {
        me = user("me", Role.USER).getId();
        target = user("target", Role.USER).getId();
        user("admin", Role.ADMIN);
    }

    @Test
    void 차단하면_저장되고_운영자에게_차단_신고와_알림이_남는다() {
        userBlockService.block(me, target);

        assertThat(userBlockService.getBlockedIds(me)).containsExactly(target);
        assertThat(userReportRepository.findAll()).singleElement().satisfies(report -> {
            assertThat(report.getReason()).isEqualTo(ReportReason.BLOCKED);
            assertThat(report.getReporterId()).isEqualTo(me);
            assertThat(report.getTargetUserId()).isEqualTo(target);
        });
        assertThat(outboxEventRepository.findAll()).singleElement()
                .satisfies(event -> assertThat(event.getEventType()).isEqualTo(ReportReceivedEvent.TYPE));
    }

    @Test
    void 이미_차단한_사람을_다시_차단해도_한_번만_남는다() {
        userBlockService.block(me, target);
        userBlockService.block(me, target);

        assertThat(userBlockRepository.findAll()).hasSize(1);
        assertThat(userReportRepository.findAll()).hasSize(1);
    }

    @Test
    void 이미_신고한_사람을_차단하면_신고를_새로_남기지_않는다() {
        userReportService.report(me, target, new ReportRequest(ReportReason.ABUSE, null));

        userBlockService.block(me, target);

        assertThat(userReportRepository.findAll()).extracting(UserReport::getReason).containsExactly(ReportReason.ABUSE);
        assertThat(userBlockService.getBlockedIds(me)).containsExactly(target);
    }

    @Test
    void 차단은_한쪽_방향이다() {
        userBlockService.block(me, target);

        assertThat(userBlockService.getBlockedIds(target)).isEmpty();
    }

    @Test
    void 자기_자신은_차단할_수_없고_없거나_탈퇴한_사용자도_차단할_수_없다() {
        User withdrawn = user("gone", Role.USER);
        withdrawn.withdraw(Instant.now());
        userRepository.flush();

        assertErrorCode(() -> userBlockService.block(me, me), UserErrorCode.CANNOT_BLOCK_SELF);
        assertErrorCode(() -> userBlockService.block(me, 9999L), UserErrorCode.USER_NOT_FOUND);
        assertErrorCode(() -> userBlockService.block(me, withdrawn.getId()), UserErrorCode.USER_NOT_FOUND);
        assertThat(userBlockRepository.findAll()).isEmpty();
    }

    @Test
    void 해제하면_다시_보이고_차단하지_않은_사람을_해제해도_성공한다() {
        userBlockService.block(me, target);

        userBlockService.unblock(me, target);
        userBlockService.unblock(me, target);

        assertThat(userBlockService.getBlockedIds(me)).isEmpty();
    }

    @Test
    void 차단_목록은_최근_차단한_순이고_탈퇴한_사람은_빠진다() {
        Long second = user("second", Role.USER).getId();
        User withdrawnLater = user("later", Role.USER);
        userBlockService.block(me, target);
        userBlockService.block(me, second);
        userBlockService.block(me, withdrawnLater.getId());
        withdrawnLater.withdraw(Instant.now());
        userRepository.flush();

        assertThat(userBlockService.getBlockedUsers(me)).extracting(BlockedUserResponse::userId)
                .containsExactly(second, target);
    }

    private User user(String providerId, Role role) {
        User user = User.createSocialUser("kakao", providerId, "닉네임", null, null);
        ReflectionTestUtils.setField(user, "role", role);
        return userRepository.saveAndFlush(user);
    }

    private static void assertErrorCode(ThrowingCallable call, UserErrorCode code) {
        assertThatThrownBy(call).isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo(code);
    }
}
