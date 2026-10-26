package com.moamap.gateway.auth;

import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import io.jsonwebtoken.ExpiredJwtException;
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

    public TokenValidation validate(String token) {
        try {
            String subject = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
            return TokenValidation.valid(Long.valueOf(subject));
        } catch (ExpiredJwtException e) {
            // 만료는 위조·형식오류와 구분한다. 앱이 리프레시로 자동 갱신할 수 있게.
            return TokenValidation.expired();
        } catch (JwtException | IllegalArgumentException e) {
            return TokenValidation.invalid();
        }
    }
}
