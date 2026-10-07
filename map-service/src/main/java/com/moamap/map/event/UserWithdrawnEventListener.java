package com.moamap.map.event;

import com.moamap.map.service.MapWithdrawalCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 탈퇴 이벤트를 받아 그 회원의 지도·참여·게시글을 정리한다.
 *
 * 같은 이벤트가 다시 와도 결과가 같도록 정리 로직이 멱등하다.
 * 오류는 잡지 않는다 — 재시도와 DLQ 라우팅은 프레임워크가 맡는다(가입 이벤트와 같은 방식).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserWithdrawnEventListener {

    private final MapWithdrawalCleanupService cleanupService;

    @RabbitListener(queues = "map-service.user-withdrawn")
    public void handle(UserWithdrawnEvent event) {
        cleanupService.cleanUp(event.userId());
        log.info("탈퇴 회원 지도 데이터 정리 완료. userId={}, eventId={}", event.userId(), event.eventId());
    }
}
