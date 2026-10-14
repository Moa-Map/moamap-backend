package com.moamap.place.event;

import com.moamap.place.service.PlaceWithdrawalCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 탈퇴 이벤트를 받아 그 회원의 하트와 장소 댓글을 정리한다.
 *
 * 같은 이벤트가 다시 와도 결과가 같도록 정리 로직이 멱등하다.
 * 오류는 잡지 않는다 — 재시도와 DLQ 라우팅은 프레임워크가 맡는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserWithdrawnEventListener {

    private final PlaceWithdrawalCleanupService cleanupService;

    @RabbitListener(queues = "place-service.user-withdrawn")
    public void handle(UserWithdrawnEvent event) {
        cleanupService.cleanUp(event.userId());
        log.info("탈퇴 회원 장소 데이터 정리 완료. userId={}, eventId={}", event.userId(), event.eventId());
    }
}
