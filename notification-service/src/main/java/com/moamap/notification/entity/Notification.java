package com.moamap.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 한 사람이 받은 알림 한 건.
 *
 * 닉네임·장소 이름 같은 문구는 저장하지 않고 ID만 둔다(NotificationType 참고).
 *
 * 삭제는 소프트 삭제가 아니라 실제 삭제다. 알림은 다른 서비스에서 일어난 일의 사본이라 지워도 원본이 남고,
 * 받은 사람이 지운 알림·탈퇴한 회원의 알림을 보관할 이유가 없다. 오래된 알림도 보관 기간이 지나면 지운다.
 */
@Entity
@Table(
        name = "notification",
        uniqueConstraints = @UniqueConstraint(name = "uk_notification_source_event", columnNames = "source_event_id"),
        indexes = {
                @Index(name = "idx_notification_recipient_created", columnList = "recipient_id, created_at"),
                @Index(name = "idx_notification_created", columnList = "created_at")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient_id", nullable = false)
    private Long recipientId;

    /** 종류가 늘어날 수 있어 CHECK 제약 없이 이름 문자열로 저장한다(EnumNameConverter 참고). */
    @Convert(converter = NotificationTypeConverter.class)
    @Column(nullable = false, length = 40)
    private NotificationType type;

    /** 알림을 일으킨 사람. 보여주지 않는 종류는 비어 있다. */
    @Column(name = "actor_id")
    private Long actorId;

    @Convert(converter = NotificationTargetTypeConverter.class)
    @Column(name = "target_type", nullable = false, length = 20)
    private NotificationTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    /** 대상이 속한 지도. 앱이 지도 화면을 거쳐 대상으로 이동할 때 쓴다. */
    @Column(name = "map_id")
    private Long mapId;

    /** 이 알림을 만든 이벤트 ID. 같은 이벤트가 다시 와도 알림이 두 번 생기지 않게 한다. */
    @Column(name = "source_event_id", nullable = false, length = 64)
    private String sourceEventId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    private Notification(Long recipientId, NotificationType type, Long actorId, Long targetId, Long mapId,
                         String sourceEventId, Instant createdAt) {
        this.recipientId = recipientId;
        this.type = type;
        this.actorId = type.showsActor() ? actorId : null;
        this.targetType = type.getTargetType();
        this.targetId = targetId;
        this.mapId = mapId;
        this.sourceEventId = sourceEventId;
        this.createdAt = createdAt;
    }

    public static Notification create(Long recipientId, NotificationType type, Long actorId, Long targetId,
                                      Long mapId, String sourceEventId, Instant createdAt) {
        return new Notification(recipientId, type, actorId, targetId, mapId, sourceEventId, createdAt);
    }

    public boolean isRead() {
        return readAt != null;
    }

    /** 이미 읽은 알림은 처음 읽은 시각을 유지한다. */
    public void markRead(Instant at) {
        if (readAt == null) {
            this.readAt = at;
        }
    }
}
