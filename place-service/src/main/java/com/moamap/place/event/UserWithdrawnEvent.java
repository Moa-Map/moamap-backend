package com.moamap.place.event;

import java.time.Instant;

/**
 * user-service가 발행하는 회원 탈퇴 이벤트의 place-service 쪽 사본. 서비스 간 클래스를 공유하지 않으므로 필드만 맞춘다.
 */
public record UserWithdrawnEvent(String eventId, Long userId, Instant occurredAt) {
}
