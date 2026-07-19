package com.moamap.gateway.auth;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import javax.crypto.SecretKey;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

// 토큰 검증하고 subject(userId) 꺼냄. user-service 발급 시크릿이랑 같아야 통과됨.
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
            return Optional.empty(); // 위조·만료·형식오류는 인증 실패로
        }
    }
}
