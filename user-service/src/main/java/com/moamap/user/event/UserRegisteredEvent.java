package com.moamap.user.event;

import java.time.Instant;

/**
 * 회원가입이 끝났음을 알리는 이벤트. user.events 익스체인지에 user.registered 라우팅 키로 발행된다.
 *
 * 소비자가 필요한 값을 스스로 조회하지 않아도 되도록 payload에 담아 보낸다.
 * eventId는 중복 제거용이 아니라 로그를 이어 보기 위한 값이다.
 */
public record UserRegisteredEvent(String eventId, Long userId, Instant occurredAt) {

    public static final String TYPE = "user.registered";
}
