package com.moamap.map.event;

import java.time.Instant;

/**
 * notification-service가 알림으로 저장하는 AMQP payload. 클래스를 공유하지 않으므로
 * 필드 이름을 notification-service의 ActivityEvent와 맞춘다. eventId로 수신 측이 중복을 거른다.
 */
public record ActivityEvent(String eventId, Long recipientId, Long actorId, Long targetId, Long mapId,
                            Instant occurredAt) {
}
