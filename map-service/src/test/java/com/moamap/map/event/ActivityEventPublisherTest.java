package com.moamap.map.event;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ActivityEventPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private ActivityEventPublisher publisher;

    @Test
    void on은_신호의_라우팅_키로_map_events에_알림_이벤트를_발행한다() {
        publisher.on(new ActivitySignal(ActivitySignal.MAP_ADMIN_ASSIGNED, 2L, 1L, 100L, 100L));

        ArgumentCaptor<ActivityEvent> captor = ArgumentCaptor.forClass(ActivityEvent.class);
        verify(rabbitTemplate).convertAndSend(eq("map.events"), eq("map.admin.assigned"), captor.capture());
        ActivityEvent event = captor.getValue();
        assertThat(event.eventId()).isNotBlank();
        assertThat(event.recipientId()).isEqualTo(2L);
        assertThat(event.actorId()).isEqualTo(1L);
        assertThat(event.targetId()).isEqualTo(100L);
        assertThat(event.mapId()).isEqualTo(100L);
        assertThat(event.occurredAt()).isNotNull();
    }

    @Test
    void on은_발행이_실패해도_예외를_전파하지_않는다() {
        willThrow(new AmqpConnectException(new RuntimeException("connection refused")))
            .given(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        assertThatCode(() -> publisher.on(new ActivitySignal(ActivitySignal.MAP_POST_COMMENTED, 2L, 3L, 7L, 10L)))
            .doesNotThrowAnyException();
    }
}
