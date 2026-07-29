package com.moamap.map.config;

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
 * map-service는 소비자다. place-service가 선언한 place.events 익스체인지에 큐를 바인딩하고,
 * 실패 메시지를 위한 DLQ를 둔다(청사진 2-2).
 */
@Configuration
public class RabbitConfig {

    private static final String EXCHANGE = "place.events";
    private static final String ROUTING_KEY = "place.count.changed";
    private static final String QUEUE = "map-service.place-count";
    private static final String DLQ = "map-service.place-count.dlq";

    @Bean
    public TopicExchange placeEventsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue placeCountQueue() {
        return QueueBuilder.durable(QUEUE)
            .withArgument("x-dead-letter-exchange", "")
            .withArgument("x-dead-letter-routing-key", DLQ)
            .build();
    }

    @Bean
    public Queue placeCountDeadLetterQueue() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    public Binding placeCountBinding(Queue placeCountQueue, TopicExchange placeEventsExchange) {
        return BindingBuilder.bind(placeCountQueue).to(placeEventsExchange).with(ROUTING_KEY);
    }

    @Bean
    public MessageConverter jackson2JsonMessageConverter(ObjectMapper objectMapper) {
        // producer가 넣은 __TypeId__(com.moamap.place.event.PlaceCountChangedEvent) 헤더로
        // 클래스를 찾으면 map-service에 그 클래스가 없어 ClassNotFoundException이 난다.
        // 리스너 메서드 파라미터 타입을 우선하도록 INFERRED로 설정한다(청사진 4장).
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence.INFERRED);
        typeMapper.setTrustedPackages("com.moamap.map.event");

        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }
}
