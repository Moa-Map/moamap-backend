package com.moamap.notification.support;

import com.moamap.notification.entity.Notification;
import com.moamap.notification.entity.NotificationType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * 테스트 공통 값. 시각은 한 곳에 고정해 두어 실행 시점에 따라 결과가 달라지지 않게 한다.
 */
public final class NotificationFixtures {

    public static final Instant NOW = Instant.parse("2026-10-20T03:00:00Z");
    public static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private NotificationFixtures() {
    }

    public static Notification notification(Long recipientId, Instant createdAt) {
        return notification(recipientId, NotificationType.PLACE_COMMENTED, 99L, createdAt);
    }

    public static Notification notification(Long recipientId, NotificationType type, Long actorId, Instant createdAt) {
        return Notification.create(recipientId, type, actorId, 10L, 20L, UUID.randomUUID().toString(), createdAt);
    }
}
