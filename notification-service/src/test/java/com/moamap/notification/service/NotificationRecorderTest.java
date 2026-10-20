package com.moamap.notification.service;

import static com.moamap.notification.support.NotificationFixtures.FIXED_CLOCK;
import static com.moamap.notification.support.NotificationFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.moamap.notification.entity.Notification;
import com.moamap.notification.entity.NotificationTargetType;
import com.moamap.notification.entity.NotificationType;
import com.moamap.notification.event.ActivityEvent;
import com.moamap.notification.repository.NotificationRepository;
import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class NotificationRecorderTest {

    private static final Long RECIPIENT = 1L;
    private static final Long ACTOR = 2L;

    @Mock
    private NotificationRepository notificationRepository;

    private NotificationRecorder recorder;

    @BeforeEach
    void setUp() {
        recorder = new NotificationRecorder(notificationRepository, FIXED_CLOCK);
    }

    @Test
    void 이벤트를_알림으로_저장한다() {
        Instant occurredAt = NOW.minusSeconds(30);

        recorder.record(NotificationType.MAP_POST_COMMENTED,
                new ActivityEvent("event-1", RECIPIENT, ACTOR, 10L, 20L, occurredAt));

        Notification saved = captureSaved();
        assertThat(saved.getRecipientId()).isEqualTo(RECIPIENT);
        assertThat(saved.getType()).isEqualTo(NotificationType.MAP_POST_COMMENTED);
        assertThat(saved.getTargetType()).isEqualTo(NotificationTargetType.MAP_POST);
        assertThat(saved.getActorId()).isEqualTo(ACTOR);
        assertThat(saved.getTargetId()).isEqualTo(10L);
        assertThat(saved.getMapId()).isEqualTo(20L);
        assertThat(saved.getSourceEventId()).isEqualTo("event-1");
        assertThat(saved.getCreatedAt()).isEqualTo(occurredAt);
        assertThat(saved.isRead()).isFalse();
    }

    @Test
    void 행동한_사람을_보여주지_않는_종류는_행동한_사람을_저장하지_않는다() {
        recorder.record(NotificationType.INQUIRY_ANSWERED, new ActivityEvent("event-1", RECIPIENT, ACTOR, 10L, null, NOW));

        assertThat(captureSaved().getActorId()).isNull();
    }

    @Test
    void 내가_한_일은_나에게_알리지_않는다() {
        recorder.record(NotificationType.PLACE_COMMENTED, new ActivityEvent("event-1", RECIPIENT, RECIPIENT, 10L, 20L, NOW));

        verify(notificationRepository, never()).saveAndFlush(any());
    }

    @Test
    void 이미_저장한_이벤트는_다시_저장하지_않는다() {
        given(notificationRepository.existsBySourceEventId("event-1")).willReturn(true);

        recorder.record(NotificationType.PLACE_APPROVED, new ActivityEvent("event-1", RECIPIENT, null, 10L, null, NOW));

        verify(notificationRepository, never()).saveAndFlush(any());
    }

    @Test
    void 동시에_처리된_중복_이벤트는_유일_제약에_걸려도_성공으로_넘긴다() {
        given(notificationRepository.existsBySourceEventId("event-1")).willReturn(false, true);
        given(notificationRepository.saveAndFlush(any())).willThrow(new DataIntegrityViolationException("duplicate"));

        recorder.record(NotificationType.PLACE_APPROVED, new ActivityEvent("event-1", RECIPIENT, null, 10L, null, NOW));
    }

    @Test
    void 중복이_아닌_저장_실패는_그대로_던져_재시도되게_한다() {
        given(notificationRepository.existsBySourceEventId("event-1")).willReturn(false, false);
        given(notificationRepository.saveAndFlush(any())).willThrow(new DataIntegrityViolationException("other"));

        assertThatThrownBy(() -> recorder.record(NotificationType.PLACE_APPROVED,
                new ActivityEvent("event-1", RECIPIENT, null, 10L, null, NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 일어난_시각이_없으면_받은_시각으로_저장한다() {
        recorder.record(NotificationType.PLACE_APPROVED, new ActivityEvent("event-1", RECIPIENT, null, 10L, null, null));

        assertThat(captureSaved().getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void 일어난_시각이_미래면_지금으로_맞춘다() {
        recorder.record(NotificationType.PLACE_APPROVED,
                new ActivityEvent("event-1", RECIPIENT, null, 10L, null, NOW.plusSeconds(600)));

        assertThat(captureSaved().getCreatedAt()).isEqualTo(NOW);
    }

    @ParameterizedTest
    @MethodSource("invalidEvents")
    void 형식이_잘못된_이벤트는_저장하지_않고_예외를_던진다(ActivityEvent event) {
        assertThatThrownBy(() -> recorder.record(NotificationType.PLACE_APPROVED, event))
                .isInstanceOf(IllegalArgumentException.class);
        verify(notificationRepository, never()).saveAndFlush(any());
    }

    static Stream<ActivityEvent> invalidEvents() {
        return Stream.of(
                null,
                new ActivityEvent(null, RECIPIENT, null, 10L, null, NOW),
                new ActivityEvent(" ", RECIPIENT, null, 10L, null, NOW),
                new ActivityEvent("x".repeat(NotificationRecorder.MAX_EVENT_ID_LENGTH + 1), RECIPIENT, null, 10L, null, NOW),
                new ActivityEvent("event-1", null, null, 10L, null, NOW),
                new ActivityEvent("event-1", 0L, null, 10L, null, NOW),
                new ActivityEvent("event-1", RECIPIENT, null, null, null, NOW));
    }

    private Notification captureSaved() {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }
}
