package com.moamap.user.refreshtoken;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis 기반 리프레시 토큰 저장소.
 * key = "refresh:{SHA-256(token)}", value = userId, TTL로 만료를 자동 처리한다.
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
        return KEY_PREFIX + sha256Hex(token);
    }

    // 토큰 원문을 Redis 키에 노출하지 않도록 단방향 해시(SHA-256)를 적용한다. save/find/delete가 모두 이 키를 쓴다.
    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }
}
