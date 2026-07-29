package com.moamap.map.event;

import com.moamap.map.repository.MapEntityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * place-service가 발행한 장소 개수 변경 이벤트를 수신해 MapEntity.placeCount를 갱신한다(청사진 3-3(나)).
 * DB 오류 등은 catch하지 않고 그대로 전파한다 — 프레임워크 재시도(3회)·DLQ 라우팅이 이를 처리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlaceCountEventListener {

    private final MapEntityRepository mapEntityRepository;

    // updatePlaceCount는 @Modifying 벌크 UPDATE라 트랜잭션 없이 호출하면 TransactionRequiredException이 난다.
    @Transactional
    @RabbitListener(queues = "map-service.place-count")
    public void handle(PlaceCountChangedEvent event) {
        int updatedRows = mapEntityRepository.updatePlaceCount(event.mapId(), event.placeCount());
        if (updatedRows == 0) {
            // 지도가 이미 삭제된 경우일 수 있어 오류가 아니다 — 정상 종료한다.
            log.info("placeCount 갱신 대상 지도를 찾지 못했다. mapId={}, eventId={}", event.mapId(), event.eventId());
        }
    }
}
