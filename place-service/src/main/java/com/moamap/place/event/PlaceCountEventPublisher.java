package com.moamap.place.event;

import java.time.Instant;
import java.util.UUID;
import com.moamap.place.entity.PlaceStatus;
import com.moamap.place.repository.PlaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 도메인 트랜잭션 커밋 이후에만 실제 AMQP 발행을 수행한다(청사진 3-1(나), 3-3(가)).
 * 발행은 부가 효과일 뿐이므로, count 조회·전송 어느 쪽에서 실패해도 원 요청에 영향을 주지 않도록
 * 여기서 모든 예외를 삼킨다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlaceCountEventPublisher {

    private static final String EXCHANGE = "place.events";
    private static final String ROUTING_KEY = "place.count.changed";

    private final PlaceRepository placeRepository;
    private final RabbitTemplate rabbitTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(PlaceCountChangeSignal signal) {
        publishNow(signal.mapId());
    }

    public void publishNow(Long mapId) {
        try {
            long placeCount = placeRepository.countByMapIdAndStatusAndDeletedAtIsNull(mapId, PlaceStatus.APPROVED);
            PlaceCountChangedEvent event = new PlaceCountChangedEvent(
                UUID.randomUUID().toString(), mapId, placeCount, Instant.now());
            rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, event);
        } catch (Exception e) {
            log.warn("장소 개수 이벤트 발행 실패: mapId={}", mapId, e);
        }
    }
}
