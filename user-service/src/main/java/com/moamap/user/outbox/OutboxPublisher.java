package com.moamap.user.outbox;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보관함에 쌓인 이벤트를 주기적으로 브로커에 보낸다.
 *
 * 발행에 실패하면 published_at을 남기지 않으므로 다음 주기에 자연히 다시 시도된다.
 * 발행 후 표시 직전에 죽으면 같은 이벤트가 두 번 나갈 수 있어, 소비자 쪽에서 중복을 견딜 수 있어야 한다.
 */
@Slf4j
@Component
public class OutboxPublisher {

    private static final String EXCHANGE = "user.events";
    private static final int BATCH_SIZE = 100;

    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;
    private final Duration retention;

    public OutboxPublisher(OutboxEventRepository outboxEventRepository,
                           RabbitTemplate rabbitTemplate,
                           @Value("${outbox.retention-days:7}") long retentionDays) {
        this.outboxEventRepository = outboxEventRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.retention = Duration.ofDays(retentionDays);
    }

    /**
     * 이전 실행이 끝난 뒤 간격을 두는 fixedDelay를 쓴다. 발행이 느려질 때 실행이 겹치지 않게 하기 위함이다.
     */
    @Scheduled(fixedDelayString = "${outbox.poll-interval-ms:2000}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> pending = outboxEventRepository.findUnpublished(Limit.of(BATCH_SIZE));
        for (OutboxEvent event : pending) {
            publish(event);
        }
    }

    /**
     * 발행에 실패한 건은 표시하지 않고 넘어간다. 한 건이 막혀도 나머지는 나갈 수 있어야 한다.
     */
    private void publish(OutboxEvent event) {
        try {
            rabbitTemplate.convertAndSend(EXCHANGE, event.getEventType(), event.getPayload(),
                message -> {
                    // 브로커가 재시작돼도 메시지가 남도록 디스크에 기록시킨다.
                    message.getMessageProperties().setDeliveryMode(MessageProperties.DEFAULT_DELIVERY_MODE);
                    message.getMessageProperties().setContentType(MessageProperties.CONTENT_TYPE_JSON);
                    return message;
                });
            event.markPublished();
        } catch (Exception e) {
            log.warn("이벤트 발행에 실패해 다음 주기에 다시 시도한다. id={}, type={}",
                event.getId(), event.getEventType(), e);
        }
    }

    /**
     * 발행이 끝난 오래된 기록을 지운다.
     */
    @Scheduled(cron = "${outbox.cleanup-cron:0 0 4 * * *}")
    @Transactional
    public void cleanUpPublished() {
        int deleted = outboxEventRepository.deletePublishedBefore(Instant.now().minus(retention));
        if (deleted > 0) {
            log.info("발행 완료된 이벤트 {}건을 정리했다.", deleted);
        }
    }
}
