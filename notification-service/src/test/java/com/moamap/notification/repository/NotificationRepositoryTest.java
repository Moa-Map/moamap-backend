package com.moamap.notification.repository;

import static com.moamap.notification.support.NotificationFixtures.NOW;
import static com.moamap.notification.support.NotificationFixtures.notification;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moamap.notification.entity.Notification;
import com.moamap.notification.entity.NotificationType;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

@DataJpaTest
class NotificationRepositoryTest {

    private static final Long ME = 1L;
    private static final Long OTHER = 2L;

    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    void 받은_사람의_알림만_조회한다() {
        notificationRepository.save(notification(ME, NOW));
        notificationRepository.save(notification(OTHER, NOW));

        assertThat(notificationRepository.findByRecipientId(ME, PageRequest.of(0, 20)))
                .extracting(Notification::getRecipientId)
                .containsExactly(ME);
    }

    @Test
    void 남의_알림은_ID로도_찾을_수_없다() {
        Notification others = notificationRepository.save(notification(OTHER, NOW));

        assertThat(notificationRepository.findByIdAndRecipientId(others.getId(), ME)).isEmpty();
    }

    @Test
    void 모두_읽음은_내_안읽은_알림만_바꾸고_이미_읽은_시각은_유지한다() {
        Instant earlier = NOW.minusSeconds(3600);
        Notification alreadyRead = notification(ME, NOW);
        alreadyRead.markRead(earlier);
        notificationRepository.save(alreadyRead);
        notificationRepository.save(notification(ME, NOW));
        Notification others = notificationRepository.save(notification(OTHER, NOW));

        int updated = notificationRepository.markAllRead(ME, NOW);

        assertThat(updated).isEqualTo(1);
        assertThat(notificationRepository.countByRecipientIdAndReadAtIsNull(ME)).isZero();
        assertThat(notificationRepository.findById(alreadyRead.getId()).orElseThrow().getReadAt()).isEqualTo(earlier);
        assertThat(notificationRepository.findById(others.getId()).orElseThrow().isRead()).isFalse();
    }

    @Test
    void 남의_알림은_지워지지_않는다() {
        Notification others = notificationRepository.save(notification(OTHER, NOW));

        assertThat(notificationRepository.deleteByIdAndRecipientId(others.getId(), ME)).isZero();
        assertThat(notificationRepository.existsById(others.getId())).isTrue();
    }

    @Test
    void 받은_사람의_알림을_한꺼번에_지운다() {
        notificationRepository.save(notification(ME, NOW));
        notificationRepository.save(notification(ME, NOW));
        notificationRepository.save(notification(OTHER, NOW));

        assertThat(notificationRepository.deleteAllByRecipientId(ME)).isEqualTo(2);
        assertThat(notificationRepository.findAll()).extracting(Notification::getRecipientId).containsExactly(OTHER);
    }

    @Test
    void 기준_시각보다_먼저_만들어진_알림만_지운다() {
        Instant threshold = NOW.minus(Duration.ofDays(90));
        notificationRepository.save(notification(ME, threshold.minusSeconds(1)));
        Notification onThreshold = notificationRepository.save(notification(ME, threshold));

        assertThat(notificationRepository.deleteCreatedBefore(threshold)).isEqualTo(1);
        assertThat(notificationRepository.findAll()).extracting(Notification::getId).containsExactly(onThreshold.getId());
    }

    @Test
    void 같은_이벤트로_만든_알림은_두_번_저장되지_않는다() {
        notificationRepository.saveAndFlush(
                Notification.create(ME, NotificationType.PLACE_APPROVED, null, 10L, null, "event-1", NOW));

        assertThatThrownBy(() -> notificationRepository.saveAndFlush(
                Notification.create(ME, NotificationType.PLACE_APPROVED, null, 10L, null, "event-1", NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
