package com.moamap.gateway.auth;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import javax.crypto.SecretKey;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

/**
 * 액세스 토큰의 서명을 검증하고 사용자 ID(subject)를 추출한다.
 * 검증에 실패하면(위조/만료/형식오류) 빈 Optional을 반환한다.
 */
@Component
public class JwtValidator {

    private final SecretKey key;

    public JwtValidator(JwtProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public Optional<Long> extractUserId(String token) {
        try {
            String subject = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
            return Optional.of(Long.valueOf(subject));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
