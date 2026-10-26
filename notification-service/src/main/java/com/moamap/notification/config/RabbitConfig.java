package com.moamap.notification.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moamap.notification.event.ActivityRoute;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * notification-service는 소비자다. 각 서비스의 익스체인지에 알림용 큐를 바인딩하고, 실패 메시지를 위한 DLQ를 둔다.
 *
 * 큐는 두 개로 나눈다. 알림 저장이 막혀도 탈퇴 정리는 계속 돌아야 하기 때문이다(반대도 같다).
 * 익스체인지는 발행하는 쪽과 같은 속성으로 함께 선언한다. 어느 쪽이 먼저 떠도 바인딩이 만들어진다.
 */
@Configuration
public class RabbitConfig {

    public static final String ACTIVITY_QUEUE = "notification-service.activity";
    private static final String ACTIVITY_DLQ = "notification-service.activity.dlq";

    public static final String USER_WITHDRAWN_QUEUE = "notification-service.user-withdrawn";
    private static final String USER_WITHDRAWN_DLQ = "notification-service.user-withdrawn.dlq";
    private static final String USER_WITHDRAWN_ROUTING_KEY = "user.withdrawn";

    @Bean
    public Declarables notificationTopology() {
        List<Declarable> declarables = new ArrayList<>();

        Arrays.stream(ActivityRoute.values())
                .map(ActivityRoute::getExchange)
                .distinct()
                .forEach(name -> declarables.add(new TopicExchange(name, true, false)));

        declarables.add(durableQueueWithDeadLetter(ACTIVITY_QUEUE, ACTIVITY_DLQ));
        declarables.add(QueueBuilder.durable(ACTIVITY_DLQ).build());
        for (ActivityRoute route : ActivityRoute.values()) {
            declarables.add(bindQueue(ACTIVITY_QUEUE, route.getExchange(), route.getRoutingKey()));
        }

        declarables.add(durableQueueWithDeadLetter(USER_WITHDRAWN_QUEUE, USER_WITHDRAWN_DLQ));
        declarables.add(QueueBuilder.durable(USER_WITHDRAWN_DLQ).build());
        declarables.add(bindQueue(USER_WITHDRAWN_QUEUE, ActivityRoute.Exchanges.USER, USER_WITHDRAWN_ROUTING_KEY));

        return new Declarables(declarables);
    }

    @Bean
    public MessageConverter jackson2JsonMessageConverter(ObjectMapper objectMapper) {
        // 발행하는 쪽이 넣은 __TypeId__ 헤더(그 서비스의 클래스 이름)로 클래스를 찾으면 여기엔 그 클래스가 없다.
        // 리스너 메서드 파라미터 타입을 우선하도록 INFERRED로 둔다(map-service와 같은 설정).
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence.INFERRED);
        typeMapper.setTrustedPackages("com.moamap.notification.event");

        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }

    private static Queue durableQueueWithDeadLetter(String queue, String deadLetterQueue) {
        return QueueBuilder.durable(queue)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", deadLetterQueue)
                .build();
    }

    private static Binding bindQueue(String queue, String exchange, String routingKey) {
        return new Binding(queue, Binding.DestinationType.QUEUE, exchange, routingKey, null);
    }
}
