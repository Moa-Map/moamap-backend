package com.moamap.gateway.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
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

        TokenValidation result = validator.validate(token);

        assertThat(result.status()).isEqualTo(TokenValidation.Status.VALID);
        assertThat(result.userId()).isEqualTo(42L);
    }

    @Test
    void 만료된_토큰이면_EXPIRED로_구분한다() {
        Instant past = Instant.now().minusSeconds(60);
        String token = Jwts.builder().subject("42")
            .issuedAt(Date.from(past.minusSeconds(60)))
            .expiration(Date.from(past))
            .signWith(key(SECRET)).compact();

        assertThat(validator.validate(token).status()).isEqualTo(TokenValidation.Status.EXPIRED);
    }

    @Test
    void 형식이_잘못된_토큰이면_INVALID다() {
        assertThat(validator.validate("not-a-jwt").status()).isEqualTo(TokenValidation.Status.INVALID);
    }

    @Test
    void 다른_시크릿으로_서명된_토큰이면_INVALID다() {
        String token = Jwts.builder().subject("42")
            .signWith(key("this-is-a-completely-different-secret-32bytes")).compact();

        assertThat(validator.validate(token).status()).isEqualTo(TokenValidation.Status.INVALID);
    }

    private static SecretKey key(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
