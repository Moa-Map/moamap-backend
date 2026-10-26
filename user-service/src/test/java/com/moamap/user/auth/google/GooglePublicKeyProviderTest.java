package com.moamap.user.auth.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.moamap.common.exception.BusinessException;
import com.moamap.user.exception.UserErrorCode;
import io.jsonwebtoken.Jwts;
import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GooglePublicKeyProviderTest {
    private static final KeyPair KEY = Jwts.SIG.RS256.keyPair().build();
    private static final Instant NOW = Instant.parse("2026-09-22T00:00:00Z");
    private final Clock clock = mock(Clock.class);
    private MockRestServiceServer server;
    private GooglePublicKeyProvider provider;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(NOW);
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new GooglePublicKeyProvider(builder.build(), clock);
    }

    @Test
    void JWKS를_조회해_kid에_맞는_공개키를_반환한다() {
        keys("key-1");
        assertThat(provider.find("key-1")).isEqualTo(KEY.getPublic());
        server.verify();
    }

    @Test
    void 캐시가_유효하면_구글을_다시_호출하지_않는다() {
        keys("key-1");
        provider.find("key-1");
        when(clock.instant()).thenReturn(NOW.plusSeconds(3599));
        assertThat(provider.find("key-1")).isEqualTo(KEY.getPublic());
        server.verify();
    }

    @Test
    void 캐시가_만료되면_JWKS를_다시_조회한다() {
        keys("key-1");
        provider.find("key-1");
        when(clock.instant()).thenReturn(NOW.plusSeconds(3601));
        server.reset();
        keys("key-1");
        assertThat(provider.find("key-1")).isEqualTo(KEY.getPublic());
        server.verify();
    }

    // 규칙 6
    @Test
    void 갱신이_5xx로_실패하면_서비스_장애로_처리한다() {
        server.expect(requestTo(GooglePublicKeyProvider.JWKS_URI)).andRespond(withServerError());
        assertUnavailable("key-1");
        server.verify();
    }

    // 규칙 6
    @ParameterizedTest
    @ValueSource(strings = {"", "not-json", "{}", "{\"keys\":[]}", "{\"keys\":[null]}"})
    void 응답_형식이_잘못되면_서비스_장애로_처리한다(String body) {
        server.expect(requestTo(GooglePublicKeyProvider.JWKS_URI))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        assertUnavailable("key-1");
    }

    // 규칙 6: 캐시가 있어도 알 수 없는 kid로 갱신을 시도하다 실패하면 503
    @Test
    void 알_수_없는_kid로_갱신을_시도하다_실패하면_서비스_장애로_처리한다() {
        keys("key-1");
        provider.find("key-1");
        when(clock.instant()).thenReturn(NOW.plusSeconds(61));
        server.reset();
        server.expect(requestTo(GooglePublicKeyProvider.JWKS_URI)).andRespond(withServerError());
        assertUnavailable("key-2");
        server.verify();
    }

    // 규칙 7
    @Test
    void 쿨다운_안에서_직전_갱신이_실패했으면_구글을_다시_호출하지_않고_장애로_처리한다() {
        server.expect(requestTo(GooglePublicKeyProvider.JWKS_URI)).andRespond(withServerError());
        assertUnavailable("key-1");
        when(clock.instant()).thenReturn(NOW.plusSeconds(30));
        assertUnavailable("key-1"); // 두 번째 요청이 서버에 가면 MockRestServiceServer가 실패시킨다
        server.verify();
    }

    // 규칙 7: 쿨다운이 지나면 다시 시도한다
    @Test
    void 쿨다운이_지나면_다시_갱신을_시도한다() {
        server.expect(requestTo(GooglePublicKeyProvider.JWKS_URI)).andRespond(withServerError());
        assertUnavailable("key-1");
        when(clock.instant()).thenReturn(NOW.plusSeconds(61));
        server.reset();
        keys("key-1");
        assertThat(provider.find("key-1")).isEqualTo(KEY.getPublic());
        server.verify();
    }

    // 규칙 7: 캐시가 있어도 kid가 없어 갱신하다 실패한 뒤 쿨다운 안이면 호출 없이 503
    @Test
    void 쿨다운_안에서_실패한_뒤_캐시에_없는_kid는_호출_없이_장애로_처리한다() {
        keys("key-1");
        provider.find("key-1");
        when(clock.instant()).thenReturn(NOW.plusSeconds(61));
        server.reset();
        server.expect(requestTo(GooglePublicKeyProvider.JWKS_URI)).andRespond(withServerError());
        assertUnavailable("key-2");
        when(clock.instant()).thenReturn(NOW.plusSeconds(90));
        assertUnavailable("key-2");
        server.verify();
    }

    // 규칙 8
    @Test
    void JWKS를_정상_수신했지만_kid가_없으면_인증_실패로_처리한다() {
        keys("key-1");
        assertThatThrownBy(() -> provider.find("other"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(UserErrorCode.INVALID_GOOGLE_TOKEN));
        server.verify();
    }

    // 규칙 8: 쿨다운 안에서 정상 수신 후 없는 kid 재요청은 호출 없이 401
    @Test
    void 정상_수신_후_쿨다운_안의_없는_kid_재요청은_호출_없이_인증_실패로_처리한다() {
        keys("key-1");
        assertThatThrownBy(() -> provider.find("other")).isInstanceOf(BusinessException.class);
        when(clock.instant()).thenReturn(NOW.plusSeconds(10));
        assertThatThrownBy(() -> provider.find("other"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(UserErrorCode.INVALID_GOOGLE_TOKEN));
        server.verify();
    }

    @Test
    void kid가_공백_null_128자_초과이면_외부_요청없이_인증_실패로_처리한다() {
        assertInvalid(" ");
        assertInvalid(null);
        assertInvalid("k".repeat(129));
        server.verify();
    }

    private void assertUnavailable(String kid) {
        assertThatThrownBy(() -> provider.find(kid)).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(UserErrorCode.GOOGLE_AUTH_UNAVAILABLE));
    }

    private void assertInvalid(String kid) {
        assertThatThrownBy(() -> provider.find(kid)).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(UserErrorCode.INVALID_GOOGLE_TOKEN));
    }

    private void keys(String kid) {
        RSAPublicKey key = (RSAPublicKey) KEY.getPublic();
        String n = Base64.getUrlEncoder().withoutPadding().encodeToString(key.getModulus().toByteArray());
        String e = Base64.getUrlEncoder().withoutPadding().encodeToString(key.getPublicExponent().toByteArray());
        server.expect(requestTo(GooglePublicKeyProvider.JWKS_URI)).andRespond(withSuccess(
                "{\"keys\":[{\"kty\":\"RSA\",\"use\":\"sig\",\"alg\":\"RS256\",\"kid\":\"" + kid
                        + "\",\"n\":\"" + n + "\",\"e\":\"" + e + "\"}]}", MediaType.APPLICATION_JSON));
    }
}
