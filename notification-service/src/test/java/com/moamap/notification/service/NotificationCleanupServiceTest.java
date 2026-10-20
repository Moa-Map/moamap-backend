package com.moamap.notification.service;

import static com.moamap.notification.support.NotificationFixtures.NOW;
import static com.moamap.notification.support.NotificationFixtures.notification;
import static org.assertj.core.api.Assertions.assertThat;

import com.moamap.notification.entity.Notification;
import com.moamap.notification.entity.NotificationType;
import com.moamap.notification.repository.NotificationRepository;
import com.moamap.notification.support.FixedClockConfig;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({NotificationCleanupService.class, FixedClockConfig.class})
class NotificationCleanupServiceTest {

    private static final Long WITHDRAWN = 1L;
    private static final Long OTHER = 2L;

    @Autowired
    private NotificationCleanupService cleanupService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    void 탈퇴한_회원이_받은_알림만_지우고_그_회원이_일으킨_알림은_남긴다() {
        notificationRepository.save(notification(WITHDRAWN, NOW));
        Notification causedByWithdrawn = notificationRepository.save(
                notification(OTHER, NotificationType.PLACE_COMMENTED, WITHDRAWN, NOW));

        int deleted = cleanupService.deleteAllReceivedBy(WITHDRAWN);

        assertThat(deleted).isEqualTo(1);
        assertThat(notificationRepository.findAll()).extracting(Notification::getId)
                .containsExactly(causedByWithdrawn.getId());
    }

    @Test
    void 같은_탈퇴_이벤트가_다시_와도_안전하다() {
        notificationRepository.save(notification(WITHDRAWN, NOW));

        cleanupService.deleteAllReceivedBy(WITHDRAWN);

        assertThat(cleanupService.deleteAllReceivedBy(WITHDRAWN)).isZero();
    }

    @Test
    void 보관_기간_90일이_지난_알림만_지운다() {
        notificationRepository.save(notification(OTHER, NOW.minus(Duration.ofDays(90)).minusSeconds(1)));
        Notification within = notificationRepository.save(notification(OTHER, NOW.minus(Duration.ofDays(89))));

        assertThat(cleanupService.deleteExpired()).isEqualTo(1);
        assertThat(notificationRepository.findAll()).extracting(Notification::getId).containsExactly(within.getId());
    }
}
