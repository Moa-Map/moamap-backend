package com.moamap.user.event;

import java.time.Instant;

/**
 * 운영자에게 "새 신고가 접수됐다"를 알리는 이벤트. user.events 익스체인지에 user.report.received 라우팅 키로 발행된다.
 *
 * notification-service가 알림으로 저장한다. 필드 이름은 그쪽 ActivityEvent와 맞춘다.
 * 신고자(actorId)는 알림에 담지 않는다.
 */
public record ReportReceivedEvent(String eventId, Long recipientId, Long targetId, Instant occurredAt) {

    public static final String TYPE = "user.report.received";
}
