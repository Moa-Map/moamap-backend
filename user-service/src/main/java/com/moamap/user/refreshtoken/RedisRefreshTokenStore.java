package com.moamap.user.refreshtoken;

import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis 기반 리프레시 토큰 저장소.
 * key = "refresh:{token}", value = userId, TTL로 만료를 자동 처리한다.
 */
@Component
@RequiredArgsConstructor
public class RedisRefreshTokenStore implements RefreshTokenStore {

    private static final String KEY_PREFIX = "refresh:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void save(String token, Long userId, Duration ttl) {
        redisTemplate.opsForValue().set(key(token), String.valueOf(userId), ttl);
    }

    @Override
    public Optional<Long> findUserId(String token) {
        String value = redisTemplate.opsForValue().get(key(token));
        return Optional.ofNullable(value).map(Long::valueOf);
    }

    @Override
    public void delete(String token) {
        redisTemplate.delete(key(token));
    }

    private String key(String token) {
        return KEY_PREFIX + token;
    }
}
