package com.moamap.user.auth.apple;

import com.moamap.common.exception.BusinessException;
import com.moamap.user.exception.UserErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;

public class AppleNonceService {
    private static final Duration TTL = Duration.ofMinutes(5);
    private final SecureRandom random = new SecureRandom();
    private final StringRedisTemplate redis;

    public AppleNonceService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public AppleNonceResponse issue() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        try {
            redis.opsForValue().set(key(nonce), "1", TTL);
        } catch (DataAccessException e) {
            throw new BusinessException(UserErrorCode.APPLE_AUTH_UNAVAILABLE);
        }
        return new AppleNonceResponse(nonce, TTL.toSeconds());
    }

    /** 토큰 검증 성공 후 호출한다. GETDEL로 동시 요청 중 하나만 nonce를 소비한다. */
    public void consume(String nonce) {
        if (!isValidFormat(nonce)) {
            throw new BusinessException(UserErrorCode.INVALID_APPLE_TOKEN);
        }
        String value;
        try {
            value = redis.opsForValue().getAndDelete(key(nonce));
        } catch (DataAccessException e) {
            throw new BusinessException(UserErrorCode.APPLE_AUTH_UNAVAILABLE);
        }
        if (!"1".equals(value)) {
            throw new BusinessException(UserErrorCode.INVALID_APPLE_TOKEN);
        }
    }

    static boolean isValidFormat(String nonce) {
        return nonce != null && nonce.matches("[A-Za-z0-9_-]{43}");
    }

    private String key(String nonce) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(nonce.getBytes(StandardCharsets.UTF_8));
            return "apple:nonce:" + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
