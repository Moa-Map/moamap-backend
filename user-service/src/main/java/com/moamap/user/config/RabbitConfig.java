package com.moamap.user.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * user-service는 발행자다. 익스체인지만 선언하고 큐와 바인딩은 소비자가 각자 관리한다.
 * 그래야 새 소비자가 붙어도 이쪽 코드를 고치지 않는다. (place-service의 발행 설정과 같은 방식)
 *
 * 이벤트 본문은 Outbox에 저장할 때 이미 JSON 문자열로 만들어두므로 별도 MessageConverter를 두지 않는다.
 */
@Configuration
public class RabbitConfig {

    @Bean
    public TopicExchange userEventsExchange() {
        return new TopicExchange("user.events", true, false);
    }
}
