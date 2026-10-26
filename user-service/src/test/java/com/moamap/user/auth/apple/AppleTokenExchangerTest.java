package com.moamap.user.auth.apple;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.moamap.common.exception.BusinessException;
import com.moamap.user.exception.UserErrorCode;
import io.jsonwebtoken.Jwts;
import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AppleTokenExchangerTest {
    private static final KeyPair KEY = Jwts.SIG.RS256.keyPair().build();
    private static final String NONCE = "a".repeat(43);
    private final AppleNonceService nonces = mock(AppleNonceService.class);
    private MockRestServiceServer server;
    private AppleTokenExchanger exchanger;

    @BeforeEach
    void setUp() {
        var clock = Clock.fixed(AppleClientSecretProviderTest.NOW, ZoneOffset.UTC);
        var props = new AppleProperties("com.moamap.ios");
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.build();
        var verifier = new AppleIdentityTokenVerifier(props, new ApplePublicKeyProvider(client, clock), nonces, clock);
        var secrets = new AppleClientSecretProvider(props, AppleClientSecretProviderTest.properties(), clock);
        exchanger = new AppleTokenExchanger(client, props, secrets, verifier, new com.fasterxml.jackson.databind.ObjectMapper());
    }

    @Test
    void 인가코드를_교환하고_반환된_ID토큰을_재검증한다() {
        expectToken("refresh-secret", b -> {});
        var result = exchanger.exchange("one-use-code", new AppleIdentity("apple-user", null, NONCE));
        assertThat(result.identity().subject()).isEqualTo("apple-user");
        assertThat(result.refreshToken()).isEqualTo("refresh-secret");
        assertThat(result.toString()).doesNotContain("refresh-secret");
        verifyNoInteractions(nonces);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"subject", "nonce", "audience", "issuer", "expired"})
    void 코드_교환_토큰이_원래_인증과_맞지_않으면_거부한다(String field) {
        expectToken("refresh", b -> {
            switch (field) {
                case "subject" -> b.subject("other-user");
                case "nonce" -> b.claim("nonce", "wrong");
                case "audience" -> b.audience().clear().add("other-app").and();
                case "issuer" -> b.issuer("other-issuer");
                case "expired" -> b.expiration(Date.from(AppleClientSecretProviderTest.NOW.minusSeconds(1)));
            }
        });
        assertError(UserErrorCode.INVALID_APPLE_TOKEN);
    }

    @Test
    void RefreshToken이_없는_응답은_기존_저장값_유지_판단을_위해_null을_반환한다() {
        expectToken(null, b -> {});
        assertThat(exchanger.exchange("one-use-code", new AppleIdentity("apple-user", null, NONCE)).refreshToken()).isNull();
    }

    @Test
    void 만료되거나_사용한_코드는_인증실패로_처리하고_재시도하지_않는다() {
        server.expect(requestTo(AppleTokenExchanger.TOKEN_URI)).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON).body("{\"error\":\"invalid_grant\"}"));
        assertError(UserErrorCode.INVALID_APPLE_TOKEN);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid_client", "invalid_request", "server_error"})
    void 서버_설정과_Apple_오류는_503으로_구분한다(String error) {
        server.expect(requestTo(AppleTokenExchanger.TOKEN_URI)).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON).body("{\"error\":\"" + error + "\"}"));
        assertError(UserErrorCode.APPLE_AUTH_UNAVAILABLE);
    }

    @Test
    void Apple_통신_장애는_503으로_처리한다() {
        server.expect(requestTo(AppleTokenExchanger.TOKEN_URI)).andRespond(withServerError());
        assertError(UserErrorCode.APPLE_AUTH_UNAVAILABLE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{}", "not-json"})
    void 잘못된_성공_응답은_거부한다(String body) {
        server.expect(requestTo(AppleTokenExchanger.TOKEN_URI)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        assertError(UserErrorCode.APPLE_AUTH_UNAVAILABLE);
    }

    private void assertError(UserErrorCode code) {
        assertThatThrownBy(() -> exchanger.exchange("one-use-code", new AppleIdentity("apple-user", null, NONCE)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }

    private void expectToken(String refresh, Consumer<io.jsonwebtoken.JwtBuilder> customize) {
        var b = Jwts.builder().header().keyId("apple-key").and().issuer("https://appleid.apple.com")
                .audience().add("com.moamap.ios").and().subject("apple-user").claim("nonce", NONCE)
                .expiration(Date.from(AppleClientSecretProviderTest.NOW.plusSeconds(300)));
        customize.accept(b);
        String token = b.signWith(KEY.getPrivate(), Jwts.SIG.RS256).compact();
        server.expect(requestTo(AppleTokenExchanger.TOKEN_URI)).andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(request -> {
                    String body = ((MockClientHttpRequest) request).getBodyAsString();
                    assertThat(body).contains("grant_type=authorization_code", "client_id=com.moamap.ios",
                            "code=one-use-code", "client_secret=").doesNotContain("redirect_uri", "refresh_token=");
                })
                .andRespond(withSuccess("{\"id_token\":\"" + token + "\",\"refresh_token\":"
                        + (refresh == null ? "null" : "\"" + refresh + "\"") + "}", MediaType.APPLICATION_JSON));
        RSAPublicKey publicKey = (RSAPublicKey) KEY.getPublic();
        String n = Base64.getUrlEncoder().withoutPadding().encodeToString(publicKey.getModulus().toByteArray());
        String e = Base64.getUrlEncoder().withoutPadding().encodeToString(publicKey.getPublicExponent().toByteArray());
        server.expect(requestTo(ApplePublicKeyProvider.JWKS_URI)).andRespond(withSuccess(
                "{\"keys\":[{\"kid\":\"apple-key\",\"kty\":\"RSA\",\"alg\":\"RS256\",\"use\":\"sig\",\"n\":\""
                        + n + "\",\"e\":\"" + e + "\"}]}", MediaType.APPLICATION_JSON));
    }
}
