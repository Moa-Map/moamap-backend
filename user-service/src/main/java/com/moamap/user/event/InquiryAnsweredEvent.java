package com.moamap.user.event;

import java.time.Instant;

/**
 * 문의 답변 완료를 알리는 이벤트. user.events 익스체인지에 inquiry.answered 라우팅 키로 발행된다.
 *
 * notification-service가 알림으로 저장한다. 필드 이름은 그쪽 ActivityEvent와 맞춘다.
 * 답변한 관리자(actorId)와 지도(mapId)는 알림에 보여주지 않으므로 보내지 않는다.
 */
public record InquiryAnsweredEvent(String eventId, Long recipientId, Long targetId, Instant occurredAt) {

    public static final String TYPE = "inquiry.answered";
}
