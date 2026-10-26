package com.moamap.user.event;

import java.time.Instant;

/**
 * 회원 탈퇴를 알리는 이벤트. user.events 익스체인지에 user.withdrawn 라우팅 키로 발행된다.
 *
 * 받는 쪽(map-service, place-service)은 이 회원의 데이터를 정리한다. 같은 이벤트가 두 번 와도 결과가 같아야 한다.
 * 개인정보는 담지 않는다. 회원 ID만으로 각 서비스가 자기 데이터를 찾을 수 있다.
 */
public record UserWithdrawnEvent(String eventId, Long userId, Instant occurredAt) {

    public static final String TYPE = "user.withdrawn";
}
