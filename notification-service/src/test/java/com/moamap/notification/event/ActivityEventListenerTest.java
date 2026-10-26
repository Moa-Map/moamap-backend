package com.moamap.notification.event;

import static com.moamap.notification.support.NotificationFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.moamap.notification.service.NotificationRecorder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ActivityEventListenerTest {

    private static final ActivityEvent EVENT = new ActivityEvent("event-1", 1L, 2L, 10L, 20L, NOW);

    @Mock
    private NotificationRecorder notificationRecorder;

    @InjectMocks
    private ActivityEventListener listener;

    @ParameterizedTest
    @EnumSource(ActivityRoute.class)
    void 라우팅_키로_알림_종류를_정한다(ActivityRoute route) {
        listener.handle(EVENT, route.getRoutingKey());

        verify(notificationRecorder).record(route.getNotificationType(), EVENT);
    }

    @Test
    void 알_수_없는_라우팅_키는_예외로_돌려보내_DLQ에_남긴다() {
        assertThatThrownBy(() -> listener.handle(EVENT, "place.count.changed"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(notificationRecorder, never()).record(any(), any());
    }
}
