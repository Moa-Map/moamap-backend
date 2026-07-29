package com.moamap.map.event;

import java.time.Instant;

/**
 * user-service가 발행한 가입 이벤트를 받기 위한 payload.
 *
 * 발행자의 클래스를 그대로 쓰지 않고 같은 모양으로 따로 둔다. 서비스 간에는 코드가 아니라 메시지 형식만 약속한다.
 */
public record UserRegisteredEvent(String eventId, Long userId, Instant occurredAt) {
}
