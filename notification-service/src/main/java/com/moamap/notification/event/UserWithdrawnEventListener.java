package com.moamap.notification.event;

import com.moamap.notification.config.RabbitConfig;
import com.moamap.notification.service.NotificationCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 탈퇴한 회원이 받은 알림을 지운다. 같은 이벤트가 다시 와도 결과가 같다(지울 것이 없을 뿐이다).
 *
 * 그 회원이 남에게 일으킨 알림은 남긴다. ID만 저장돼 있어 조회할 때 "탈퇴한 사용자"로 보인다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserWithdrawnEventListener {

    private final NotificationCleanupService cleanupService;

    @RabbitListener(queues = RabbitConfig.USER_WITHDRAWN_QUEUE)
    public void handle(UserWithdrawnEvent event) {
        int deleted = cleanupService.deleteAllReceivedBy(event.userId());
        log.info("탈퇴 회원 알림 정리 완료. userId={}, deleted={}, eventId={}", event.userId(), deleted, event.eventId());
    }
}
