package com.moamap.notification.event;

import java.time.Instant;

/**
 * 다른 서비스가 "무슨 일이 있었는지" 알리는 이벤트. 어떤 일인지는 라우팅 키가 말하고(ActivityRoute),
 * 본문은 모든 종류가 같은 모양을 쓴다. 서비스 간에는 클래스를 공유하지 않으므로 발행하는 쪽은 필드 이름만 맞춘다.
 *
 * @param eventId     이벤트마다 고유한 값(UUID 권장, 64자 이하). 같은 이벤트가 다시 와도 알림은 한 번만 생긴다
 * @param recipientId 알림을 받을 회원
 * @param actorId     그 일을 한 회원. 없으면 null
 * @param targetId    알림을 눌렀을 때 열 대상의 ID(장소·지도·게시글·문의)
 * @param mapId       대상이 속한 지도. 없으면 null
 * @param occurredAt  일이 일어난 시각. 알림 시각으로 쓴다
 */
public record ActivityEvent(
        String eventId,
        Long recipientId,
        Long actorId,
        Long targetId,
        Long mapId,
        Instant occurredAt
) {
}
