package com.moamap.place.event;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 커밋된 일만 알림으로 보낸다. 알림은 부가 효과라 발행이 실패해도 원 요청에 영향을 주지 않도록 예외를 삼킨다
 * (PlaceCountEventPublisher와 같은 방식). 커밋 직후 브로커가 죽어 있으면 그 알림은 빠진다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActivityEventPublisher {

    private static final String EXCHANGE = "place.events";

    private final RabbitTemplate rabbitTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(ActivitySignal signal) {
        try {
            ActivityEvent event = new ActivityEvent(UUID.randomUUID().toString(), signal.recipientId(),
                signal.actorId(), signal.targetId(), signal.mapId(), Instant.now());
            rabbitTemplate.convertAndSend(EXCHANGE, signal.routingKey(), event);
        } catch (Exception e) {
            log.warn("알림 이벤트 발행 실패: {}", signal, e);
        }
    }
}
