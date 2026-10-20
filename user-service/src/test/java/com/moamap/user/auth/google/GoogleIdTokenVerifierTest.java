package com.moamap.user.auth.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.moamap.common.exception.BusinessException;
import com.moamap.user.exception.UserErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import java.security.KeyPair;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GoogleIdTokenVerifierTest {
    private static final KeyPair KEY = Jwts.SIG.RS256.keyPair().build();
    private static final KeyPair OTHER_KEY = Jwts.SIG.RS256.keyPair().build();
    private static final Instant NOW = Instant.parse("2026-09-22T00:00:00Z");
    private static final String CLIENT_ID = "web-id.apps.googleusercontent.com";
    private final Clock clock = mock(Clock.class);
    private final GooglePublicKeyProvider keys = mock(GooglePublicKeyProvider.class);
    private GoogleIdTokenVerifier verifier;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(NOW);
        when(keys.find("key-1")).thenReturn(KEY.getPublic());
        verifier = new GoogleIdTokenVerifier(new GoogleProperties(List.of(CLIENT_ID, "ios-id")), keys, clock);
    }

    @Test
    void 정상_토큰은_claims를_반환한다() {
        Claims claims = verifier.verify(token(b -> {}));
        assertThat(claims.getSubject()).isEqualTo("google-user");
        assertThat(claims.get("email", String.class)).isEqualTo("user@example.com");
    }

    @ParameterizedTest
    @ValueSource(strings = {"accounts.google.com", "https://accounts.google.com"})
    void 허용된_iss_두_값은_통과한다(String iss) {
        assertThat(verifier.verify(token(b -> b.issuer(iss))).getSubject()).isEqualTo("google-user");
    }

    @Test
    void 허용된_clientId_중_어느_것이든_aud로_통과한다() {
        assertThat(verifier.verify(token(b -> b.audience().clear().add("ios-id").and())).getSubject())
                .isEqualTo("google-user");
    }

    @Test
    void sub가_255자면_통과한다() {
        assertThat(verifier.verify(token(b -> b.subject("a".repeat(255)))).getSubject()).hasSize(255);
    }

    // 규칙 2
    @Test
    void 토큰_길이가_16384를_넘으면_공급자_조회_없이_거부한다() {
        assertInvalid("a".repeat(16385));
        verifyNoInteractions(keys);
    }

    // 규칙 3
    @Test
    void JWT_형식이_아니면_거부한다() {
        assertInvalid("not-a-jwt");
        assertInvalid("a.b");
        assertInvalid("a.b.c.d.e"); // JWE 형태(5 파트)
        verifyNoInteractions(keys);
    }

    // 규칙 3
    @Test
    void 서명이_없는_토큰은_거부한다() {
        assertInvalid(base().compact());
        verifyNoInteractions(keys);
    }

    // 규칙 4
    @Test
    void alg가_RS256이_아니면_거부한다() {
        assertInvalid(base().signWith(KEY.getPrivate(), Jwts.SIG.RS512).compact());
    }

    // 규칙 4
    @Test
    void zip_헤더가_있으면_거부한다() {
        assertInvalid(base().compressWith(Jwts.ZIP.DEF).signWith(KEY.getPrivate(), Jwts.SIG.RS256).compact());
    }

    // 규칙 5
    @Test
    void kid가_없으면_거부한다() {
        assertInvalid(Jwts.builder().issuer("https://accounts.google.com").subject("s")
                .signWith(KEY.getPrivate(), Jwts.SIG.RS256).compact());
    }

    // 규칙 5
    @Test
    void kid가_공백이거나_128자를_넘으면_거부한다() {
        assertInvalid(token(b -> b.header().keyId(" ").and()));
        assertInvalid(token(b -> b.header().keyId("k".repeat(129)).and()));
    }

    // 규칙 9
    @Test
    void 다른_키로_서명한_토큰은_거부한다() {
        assertInvalid(base().signWith(OTHER_KEY.getPrivate(), Jwts.SIG.RS256).compact());
    }

    // 규칙 10
    @Test
    void exp가_없으면_거부한다() {
        assertInvalid(token(b -> b.expiration(null)));
    }

    // 규칙 10: 허용 오차 0이므로 exp == now도 만료
    @Test
    void exp가_지금과_같거나_과거이면_거부한다() {
        assertInvalid(token(b -> b.expiration(Date.from(NOW))));
        assertInvalid(token(b -> b.expiration(Date.from(NOW.minusSeconds(1)))));
    }

    // 규칙 11
    @Test
    void 허용되지_않은_iss이거나_없으면_거부한다() {
        assertInvalid(token(b -> b.issuer("https://attacker.example")));
        assertInvalid(token(b -> b.issuer("accounts.google.com.evil.example")));
        assertInvalid(token(b -> b.issuer(null)));
    }

    // 규칙 12
    @Test
    void aud가_허용_목록에_없으면_거부한다() {
        assertInvalid(token(b -> b.audience().clear().add("other-app").and()));
    }

    // 규칙 12
    @Test
    void aud가_정확히_1개가_아니면_거부한다() {
        assertInvalid(token(b -> b.audience().clear().and()));
        assertInvalid(token(b -> b.audience().clear().add(CLIENT_ID).add("ios-id").and()));
    }

    // 규칙 13
    @Test
    void sub가_없거나_공백이거나_255자를_넘으면_거부한다() {
        assertInvalid(token(b -> b.subject(null)));
        assertInvalid(token(b -> b.subject(" ")));
        assertInvalid(token(b -> b.subject("a".repeat(256))));
    }

    // 규칙 6·7: verifier 경계에서 장애가 401로 바뀌지 않아야 한다
    @Test
    void 공급자가_장애를_던지면_그대로_전파한다() {
        when(keys.find(any())).thenThrow(new BusinessException(UserErrorCode.GOOGLE_AUTH_UNAVAILABLE));
        assertThatThrownBy(() -> verifier.verify(token(b -> {})))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(UserErrorCode.GOOGLE_AUTH_UNAVAILABLE));
    }

    // 규칙 8: 공급자가 던진 인증 실패도 그대로 유지
    @Test
    void 공급자가_kid_없음을_던지면_인증_실패로_유지한다() {
        when(keys.find(any())).thenThrow(new BusinessException(UserErrorCode.INVALID_GOOGLE_TOKEN));
        assertInvalid(token(b -> {}));
    }

    private JwtBuilder base() {
        return Jwts.builder().header().keyId("key-1").and()
                .issuer("https://accounts.google.com").audience().add(CLIENT_ID).and()
                .subject("google-user").expiration(Date.from(NOW.plusSeconds(300)))
                .claim("email", "user@example.com");
    }

    private String token(Consumer<JwtBuilder> customize) {
        JwtBuilder builder = base();
        customize.accept(builder);
        return builder.signWith(KEY.getPrivate(), Jwts.SIG.RS256).compact();
    }

    private void assertInvalid(String token) {
        assertThatThrownBy(() -> verifier.verify(token)).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(UserErrorCode.INVALID_GOOGLE_TOKEN));
    }
}
