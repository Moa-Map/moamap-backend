package com.moamap.user.refreshtoken;

import java.time.Duration;
import java.util.Optional;

/**
 * 리프레시 토큰 저장소 추상화. 구현체(Redis 등)를 교체해도 사용하는 쪽은 바뀌지 않는다.
 */
public interface RefreshTokenStore {

    /** 토큰을 저장하고 ttl 후 자동 만료시킨다. */
    void save(String token, Long userId, Duration ttl);

    /** 토큰이 유효하면 소유자 userId를 반환한다. 없거나 만료됐으면 비어 있음. */
    Optional<Long> findUserId(String token);

    /** 토큰을 삭제한다(로그아웃). */
    void delete(String token);
}
