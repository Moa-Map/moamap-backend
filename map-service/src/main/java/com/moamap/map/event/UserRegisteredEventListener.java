package com.moamap.map.event;

import com.moamap.map.service.MapService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 가입 이벤트를 받아 그 사용자의 나만의 지도를 만든다.
 *
 * 같은 이벤트가 다시 올 수 있으므로 생성은 멱등하게 처리한다.
 * DB 오류 등은 잡지 않고 그대로 올려보낸다 — 재시도와 DLQ 라우팅은 프레임워크가 맡는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserRegisteredEventListener {

    private final MapService mapService;

    @Value("${map.personal-map.name:나만의 지도}")
    private String personalMapName;

    @RabbitListener(queues = "map-service.user-registered")
    public void handle(UserRegisteredEvent event) {
        mapService.createPersonalMapIfAbsent(event.userId(), personalMapName);
        log.info("나만의 지도 생성 처리 완료. userId={}, eventId={}", event.userId(), event.eventId());
    }
}
