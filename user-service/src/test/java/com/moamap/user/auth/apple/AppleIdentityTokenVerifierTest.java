package com.moamap.user.auth.apple;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.moamap.common.exception.BusinessException;
import com.moamap.user.exception.UserErrorCode;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtBuilder;
import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AppleIdentityTokenVerifierTest {
    private static final KeyPair KEY = Jwts.SIG.RS256.keyPair().build();
    private static final KeyPair OTHER_KEY = Jwts.SIG.RS256.keyPair().build();
    private static final Instant NOW = Instant.parse("2026-09-22T00:00:00Z");
    private static final String NONCE = "a".repeat(43);
    private final Clock clock = mock(Clock.class);
    private final AppleNonceService nonces = mock(AppleNonceService.class);
    private MockRestServiceServer server;
    private AppleIdentityTokenVerifier verifier;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(NOW);
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        verifier = new AppleIdentityTokenVerifier(new AppleProperties("com.moamap.ios"),
                new ApplePublicKeyProvider(builder.build(), clock), nonces, clock);
    }

    @Test
    void 서명과_claim을_검증한_후에만_nonce를_소비한다() {
        keys(KEY, "key-1");
        AppleIdentity identity = verifier.verifyAndConsume(token(b -> {}), NONCE);
        assertThat(identity.subject()).isEqualTo("apple-user");
        assertThat(identity.email()).isEqualTo("relay@example.com");
        verify(nonces).consume(NONCE);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"issuer", "audience", "expired", "no-exp", "subject", "nonce", "no-nonce", "no-audience", "no-issuer", "long-subject", "wrong-type"})
    void 잘못된_claim이면_nonce를_소비하지_않는다(String field) {
        keys(KEY, "key-1");
        String jwt = token(b -> {
            switch (field) {
                case "issuer" -> b.issuer("https://attacker.example");
                case "audience" -> b.audience().clear().add("other-app").and();
                case "expired" -> b.expiration(Date.from(NOW.minusSeconds(1)));
                case "no-exp" -> b.expiration(null);
                case "subject" -> b.subject(" ");
                case "nonce" -> b.claim("nonce", "wrong-nonce");
                case "no-nonce" -> b.claim("nonce", null);
                case "no-audience" -> b.audience().clear().and();
                case "no-issuer" -> b.issuer(null);
                case "long-subject" -> b.subject("a".repeat(256));
                case "wrong-type" -> b.claim("nonce", 123);
            }
        });
        assertInvalid(() -> verifier.verifyAndConsume(jwt, NONCE));
        verifyNoInteractions(nonces);
    }

    @Test
    void 다른_키로_서명한_토큰은_거부한다() {
        keys(OTHER_KEY, "key-1");
        assertInvalid(() -> verifier.verifyAndConsume(token(b -> {}), NONCE));
        verifyNoInteractions(nonces);
    }

    @Test
    void 허용하지_않은_알고리즘과_서명없는_토큰은_거부한다() {
        String wrongAlgorithm = base().signWith(KEY.getPrivate(), Jwts.SIG.RS512).compact();
        assertInvalid(() -> verifier.verifyAndConsume(wrongAlgorithm, NONCE));
        assertInvalid(() -> verifier.verifyAndConsume(base().compact(), NONCE));
        verifyNoInteractions(nonces);
        server.verify(); // 공개키 요청도 없어야 한다.
    }

    @Test
    void 같은_키는_캐시하고_알수없는_kid는_재조회_폭주를_막는다() {
        keys(KEY, "key-1");
        verifier.verify(token(b -> {}), NONCE);
        verifier.verify(token(b -> {}), NONCE);
        for (int i = 0; i < 5; i++) {
            String kid = "unknown-" + i;
            assertInvalid(() -> verifier.verify(token(b -> b.header().keyId(kid).and()), NONCE));
        }
        server.verify();
    }

    @Test
    void 재조회_간격_이후_새_kid를_받으면_키를_갱신한다() {
        keys(KEY, "key-1");
        keys(KEY, "key-2");
        verifier.verify(token(b -> {}), NONCE);
        when(clock.instant()).thenReturn(NOW.plusSeconds(61));
        verifier.verify(token(b -> b.header().keyId("key-2").and()), NONCE);
        server.verify();
    }

    @Test
    void 공개키_서버_장애는_인증실패와_구분하고_재시도를_제한한다() {
        server.expect(requestTo(ApplePublicKeyProvider.JWKS_URI)).andRespond(withServerError());
        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> verifier.verifyAndConsume(token(b -> {}), NONCE))
                    .isInstanceOfSatisfying(BusinessException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(UserErrorCode.APPLE_AUTH_UNAVAILABLE));
        }
        verifyNoInteractions(nonces);
        server.verify();
    }

    @Test
    void 이메일_없이도_검증할_수_있고_순수검증은_nonce를_소비하지_않는다() {
        keys(KEY, "key-1");
        assertThat(verifier.verify(token(b -> b.claim("email", null)), NONCE).email()).isNull();
        verifyNoInteractions(nonces);
    }

    @Test
    void nonce_재사용은_유효한_토큰이어도_거부한다() {
        keys(KEY, "key-1");
        doThrow(new BusinessException(UserErrorCode.INVALID_APPLE_TOKEN)).when(nonces).consume(NONCE);
        assertInvalid(() -> verifier.verifyAndConsume(token(b -> {}), NONCE));
    }

    @Test
    void 캐시_유효기간이_지나면_동일한_kid도_다시_조회한다() {
        keys(KEY, "key-1");
        keys(KEY, "key-1");
        String jwt = token(b -> b.expiration(Date.from(NOW.plusSeconds(7200))));
        verifier.verify(jwt, NONCE);
        when(clock.instant()).thenReturn(NOW.plusSeconds(3601));
        verifier.verify(jwt, NONCE);
        server.verify();
    }

    @Test
    void 토큰의_키_URL은_무시하고_Apple_고정주소만_조회한다() {
        keys(KEY, "key-1");
        verifier.verify(token(b -> b.header().add("jku", "https://attacker.example/keys").and()), NONCE);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not-json", "{}", "{\"keys\":[]}", "{\"keys\":[null]}"})
    void 잘못된_공개키_응답은_서비스_장애로_처리한다(String body) {
        server.expect(requestTo(ApplePublicKeyProvider.JWKS_URI))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> verifier.verify(token(b -> {}), NONCE))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(UserErrorCode.APPLE_AUTH_UNAVAILABLE));
    }

    @Test
    void 갱신_실패_중에도_유효기간이_남은_기존_키는_사용한다() {
        keys(KEY, "key-1");
        server.expect(requestTo(ApplePublicKeyProvider.JWKS_URI)).andRespond(withServerError());
        keys(KEY, "key-2");
        verifier.verify(token(b -> {}), NONCE);
        when(clock.instant()).thenReturn(NOW.plusSeconds(61));
        String newToken = token(b -> b.header().keyId("key-2").and());
        assertThatThrownBy(() -> verifier.verify(newToken, NONCE)).isInstanceOf(BusinessException.class);
        verifier.verify(token(b -> {}), NONCE);
        when(clock.instant()).thenReturn(NOW.plusSeconds(122));
        verifier.verify(newToken, NONCE);
        server.verify();
    }

    @Test
    void 형식이_잘못된_입력은_외부_요청없이_거부한다() {
        assertInvalid(() -> verifier.verify(null, NONCE));
        assertInvalid(() -> verifier.verify("not-a-jwt", NONCE));
        assertInvalid(() -> verifier.verify("a".repeat(16385), NONCE));
        assertInvalid(() -> verifier.verify(token(b -> {}), null));
        assertInvalid(() -> verifier.verify(token(b -> {}), "invalid"));
        verifyNoInteractions(nonces);
        server.verify();
    }

    private JwtBuilder base() {
        return Jwts.builder().header().keyId("key-1").and()
                .issuer("https://appleid.apple.com").audience().add("com.moamap.ios").and()
                .subject("apple-user").expiration(Date.from(NOW.plusSeconds(300)))
                .claim("nonce", NONCE).claim("email", "relay@example.com");
    }

    private String token(Consumer<JwtBuilder> customize) {
        JwtBuilder builder = base();
        customize.accept(builder);
        return builder.signWith(KEY.getPrivate(), Jwts.SIG.RS256).compact();
    }

    private void keys(KeyPair pair, String kid) {
        RSAPublicKey key = (RSAPublicKey) pair.getPublic();
        String n = Base64.getUrlEncoder().withoutPadding().encodeToString(key.getModulus().toByteArray());
        String e = Base64.getUrlEncoder().withoutPadding().encodeToString(key.getPublicExponent().toByteArray());
        server.expect(requestTo(ApplePublicKeyProvider.JWKS_URI)).andRespond(withSuccess(
                "{\"keys\":[{\"kty\":\"RSA\",\"use\":\"sig\",\"alg\":\"RS256\",\"kid\":\"" + kid
                        + "\",\"n\":\"" + n + "\",\"e\":\"" + e + "\"}]}", MediaType.APPLICATION_JSON));
    }

    private void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(UserErrorCode.INVALID_APPLE_TOKEN));
    }
}
