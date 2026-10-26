package com.moamap.notification.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.moamap.notification.event.ActivityRoute;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;

class RabbitConfigTest {

    private final Declarables topology = new RabbitConfig().notificationTopology();

    @Test
    void 알림_종류마다_알림_큐로_바인딩한다() {
        List<Binding> activityBindings = topology.getDeclarablesByType(Binding.class).stream()
                .filter(binding -> binding.getDestination().equals(RabbitConfig.ACTIVITY_QUEUE))
                .toList();

        assertThat(activityBindings).hasSize(ActivityRoute.values().length);
        for (ActivityRoute route : ActivityRoute.values()) {
            assertThat(activityBindings).anySatisfy(binding -> {
                assertThat(binding.getExchange()).isEqualTo(route.getExchange());
                assertThat(binding.getRoutingKey()).isEqualTo(route.getRoutingKey());
            });
        }
    }

    @Test
    void 탈퇴_이벤트는_별도_큐로_받는다() {
        assertThat(topology.getDeclarablesByType(Binding.class)).anySatisfy(binding -> {
            assertThat(binding.getDestination()).isEqualTo(RabbitConfig.USER_WITHDRAWN_QUEUE);
            assertThat(binding.getExchange()).isEqualTo("user.events");
            assertThat(binding.getRoutingKey()).isEqualTo("user.withdrawn");
        });
    }

    @Test
    void 처리에_실패한_메시지는_각자의_DLQ로_간다() {
        List<Queue> queues = topology.getDeclarablesByType(Queue.class);

        assertThat(queues).extracting(Queue::getName).containsExactlyInAnyOrder(
                RabbitConfig.ACTIVITY_QUEUE, RabbitConfig.ACTIVITY_QUEUE + ".dlq",
                RabbitConfig.USER_WITHDRAWN_QUEUE, RabbitConfig.USER_WITHDRAWN_QUEUE + ".dlq");
        assertThat(queues).filteredOn(queue -> !queue.getName().endsWith(".dlq")).allSatisfy(queue -> {
            assertThat(queue.isDurable()).isTrue();
            assertThat(queue.getArguments()).containsEntry("x-dead-letter-exchange", "")
                    .containsEntry("x-dead-letter-routing-key", queue.getName() + ".dlq");
        });
    }

    @Test
    void 익스체인지는_발행하는_쪽과_같은_durable_topic으로_한_번씩_선언한다() {
        List<TopicExchange> exchanges = topology.getDeclarablesByType(TopicExchange.class);

        assertThat(exchanges).extracting(TopicExchange::getName)
                .containsExactlyInAnyOrder("place.events", "map.events", "user.events");
        assertThat(exchanges).allSatisfy(exchange -> {
            assertThat(exchange.isDurable()).isTrue();
            assertThat(exchange.isAutoDelete()).isFalse();
        });
    }
}
