package com.moamap.notification.event;

import com.moamap.notification.config.RabbitConfig;
import com.moamap.notification.entity.NotificationType;
import com.moamap.notification.service.NotificationRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * 다른 서비스에서 일어난 일을 받아 알림으로 저장한다.
 *
 * 오류는 잡지 않는다 — 잡으면 메시지가 성공 처리돼 사라진다. 재시도와 DLQ 라우팅은 프레임워크가 맡는다.
 */
@Component
@RequiredArgsConstructor
public class ActivityEventListener {

    private final NotificationRecorder notificationRecorder;

    @RabbitListener(queues = RabbitConfig.ACTIVITY_QUEUE)
    public void handle(@Payload ActivityEvent event, @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey) {
        NotificationType type = ActivityRoute.fromRoutingKey(routingKey)
                .map(ActivityRoute::getNotificationType)
                .orElseThrow(() -> new IllegalArgumentException("알림으로 바꿀 수 없는 라우팅 키입니다: " + routingKey));
        notificationRecorder.record(type, event);
    }
}
