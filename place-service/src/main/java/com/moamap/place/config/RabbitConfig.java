package com.moamap.place.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
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
 * place-service는 place.events의 발행자이자 user.events의 소비자다.
 *
 * 발행: exchange만 선언하고 큐/바인딩은 소비자(map-service)가 관리한다(청사진 2-2, 4장).
 * 소비: 회원 탈퇴 이벤트를 전용 큐 + DLQ로 받는다. map-service의 가입 이벤트 구독과 같은 구성이다.
 *
 * Boot가 관리하는 ObjectMapper(JavaTimeModule 등록됨)를 그대로 써서 Instant 직렬화를 별도로 신경 쓰지 않는다.
 * RabbitTemplate은 여기서 직접 만들지 않는다 — Boot의 RabbitAutoConfiguration이 이 MessageConverter 빈을
 * 자동으로 적용하면서, spring.rabbitmq.template.* 설정 프로퍼티(retry, mandatory 등)도 함께 반영해준다.
 */
@Configuration
public class RabbitConfig {

    private static final String USER_EXCHANGE = "user.events";
    private static final String USER_WITHDRAWN_ROUTING_KEY = "user.withdrawn";
    private static final String USER_WITHDRAWN_QUEUE = "place-service.user-withdrawn";
    private static final String USER_WITHDRAWN_DLQ = "place-service.user-withdrawn.dlq";

    @Bean
    public TopicExchange placeEventsExchange() {
        return new TopicExchange("place.events", true, false);
    }

    @Bean
    public TopicExchange userEventsExchange() {
        return new TopicExchange(USER_EXCHANGE, true, false);
    }

    @Bean
    public Queue userWithdrawnQueue() {
        return QueueBuilder.durable(USER_WITHDRAWN_QUEUE)
            .withArgument("x-dead-letter-exchange", "")
            .withArgument("x-dead-letter-routing-key", USER_WITHDRAWN_DLQ)
            .build();
    }

    @Bean
    public Queue userWithdrawnDeadLetterQueue() {
        return QueueBuilder.durable(USER_WITHDRAWN_DLQ).build();
    }

    @Bean
    public Binding userWithdrawnBinding(Queue userWithdrawnQueue, TopicExchange userEventsExchange) {
        return BindingBuilder.bind(userWithdrawnQueue).to(userEventsExchange).with(USER_WITHDRAWN_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jackson2JsonMessageConverter(ObjectMapper objectMapper) {
        // user-service는 타입 헤더 없이 JSON 문자열을 보낸다. 리스너 파라미터 타입으로 역직렬화하도록 INFERRED로 두고,
        // 헤더로 클래스를 지정하더라도 이 서비스의 이벤트 패키지 밖은 거부한다(map-service와 같은 설정).
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence.INFERRED);
        typeMapper.setTrustedPackages("com.moamap.place.event");

        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }
}
