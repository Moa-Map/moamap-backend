package com.moamap.notification.service;

import com.moamap.notification.entity.Notification;
import com.moamap.notification.entity.NotificationType;
import com.moamap.notification.event.ActivityEvent;
import com.moamap.notification.repository.NotificationRepository;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 받은 이벤트를 알림으로 저장한다.
 *
 * 메시지는 최소 한 번 배달되므로 같은 이벤트가 다시 올 수 있다. 두 겹으로 막는다.
 * ① 이미 저장한 이벤트인지 먼저 확인해 흔한 중복을 싸게 거르고,
 * ② 동시에 처리돼 ①을 함께 통과한 경우는 source_event_id 유일 제약이 막는다.
 *
 * ②의 예외를 잡으려면 저장이 바깥 트랜잭션에 묶이면 안 된다(묶이면 롤백 전용으로 표시돼 커밋 때 다시 터진다).
 * 그래서 이 클래스에는 트랜잭션을 걸지 않고, 저장은 리포지토리 자체 트랜잭션으로 끝낸다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationRecorder {

    static final int MAX_EVENT_ID_LENGTH = 64;

    private final NotificationRepository notificationRepository;
    private final Clock clock;

    public void record(NotificationType type, ActivityEvent event) {
        validate(event);
        if (event.actorId() != null && event.actorId().equals(event.recipientId())) {
            // 내가 한 일은 나에게 알리지 않는다(내 장소에 내가 댓글을 단 경우 등).
            return;
        }
        if (notificationRepository.existsBySourceEventId(event.eventId())) {
            return;
        }

        Notification notification = Notification.create(event.recipientId(), type, event.actorId(),
                event.targetId(), event.mapId(), event.eventId(), notifiedAt(event));
        try {
            notificationRepository.saveAndFlush(notification);
        } catch (DataIntegrityViolationException e) {
            if (!notificationRepository.existsBySourceEventId(event.eventId())) {
                throw e;
            }
            log.debug("동시에 처리된 중복 이벤트라 건너뛴다. eventId={}", event.eventId());
        }
    }

    /**
     * 알림 시각은 일이 일어난 시각이다. 큐에 잠시 머물렀다 처리돼도 "3분 전"처럼 실제 시각으로 보인다.
     * 보낸 서버 시계가 앞서 있어 미래 시각이 오면 지금으로 맞춘다(목록 맨 위에 계속 붙어 있지 않게).
     */
    private Instant notifiedAt(ActivityEvent event) {
        Instant now = clock.instant();
        if (event.occurredAt() == null || event.occurredAt().isAfter(now)) {
            return now;
        }
        return event.occurredAt();
    }

    /**
     * 형식이 잘못된 이벤트는 저장하지 않고 예외로 돌려보낸다. 재시도 후 DLQ에 남아 원인을 확인할 수 있다.
     */
    private static void validate(ActivityEvent event) {
        if (event == null
                || event.eventId() == null || event.eventId().isBlank()
                || event.eventId().length() > MAX_EVENT_ID_LENGTH
                || event.recipientId() == null || event.recipientId() <= 0
                || event.targetId() == null) {
            throw new IllegalArgumentException("알림 이벤트 형식이 올바르지 않습니다: " + event);
        }
    }
}
