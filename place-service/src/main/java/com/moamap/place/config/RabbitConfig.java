package com.moamap.place.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * place-service는 발행자다. exchange만 선언하고 큐/바인딩은 소비자(map-service)가 관리한다(청사진 2-2, 4장).
 * Boot가 관리하는 ObjectMapper(JavaTimeModule 등록됨)를 그대로 써서 Instant 직렬화를 별도로 신경 쓰지 않는다.
 * RabbitTemplate은 여기서 직접 만들지 않는다 — Boot의 RabbitAutoConfiguration이 이 MessageConverter 빈을
 * 자동으로 적용하면서, spring.rabbitmq.template.* 설정 프로퍼티(retry, mandatory 등)도 함께 반영해준다.
 */
@Configuration
public class RabbitConfig {

    @Bean
    public TopicExchange placeEventsExchange() {
        return new TopicExchange("place.events", true, false);
    }

    @Bean
    public MessageConverter jackson2JsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
