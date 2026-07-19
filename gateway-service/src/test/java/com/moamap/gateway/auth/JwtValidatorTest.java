package com.moamap.gateway.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

class JwtValidatorTest {

    private static final String SECRET = "test-only-jwt-secret-not-for-production-32bytes";

    private final JwtValidator validator = new JwtValidator(new JwtProperties(SECRET));

    @Test
    void 유효한_토큰이면_userId를_추출한다() {
        String token = Jwts.builder().subject("42").signWith(key(SECRET)).compact();
        assertThat(validator.extractUserId(token)).contains(42L);
    }

    @Test
    void 형식이_잘못된_토큰이면_비어있다() {
        assertThat(validator.extractUserId("not-a-jwt")).isEmpty();
    }

    @Test
    void 다른_시크릿으로_서명된_토큰이면_비어있다() {
        String token = Jwts.builder().subject("42")
            .signWith(key("this-is-a-completely-different-secret-32bytes")).compact();
        assertThat(validator.extractUserId(token)).isEmpty();
    }

    private static SecretKey key(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
