package com.moamap.user.auth.apple;

import static org.assertj.core.api.Assertions.*;
import io.jsonwebtoken.Jwts;
import java.security.KeyPair;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import org.junit.jupiter.api.Test;

class AppleClientSecretProviderTest {
    static final KeyPair KEY = Jwts.SIG.ES256.keyPair().build();
    static final Instant NOW = Instant.parse("2026-09-23T00:00:00Z");

    static AppleTokenProperties properties() {
        return new AppleTokenProperties("TEAM123456", "KEY1234567", pem(KEY));
    }

    static String pem(KeyPair pair) {
        return "-----BEGIN PRIVATE KEY-----\n" + Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----";
    }

    @Test
    void 개인키로_서명한_client_secret에_앱과_팀_정보를_넣는다() {
        AppleClientSecretProvider provider = new AppleClientSecretProvider(new AppleProperties("com.moamap.ios"),
                properties(), Clock.fixed(NOW, ZoneOffset.UTC));
        var jwt = Jwts.parser().verifyWith(KEY.getPublic()).clock(() -> Date.from(NOW))
                .build().parseSignedClaims(provider.create());
        assertThat(jwt.getHeader().getAlgorithm()).isEqualTo("ES256");
        assertThat(jwt.getHeader().getKeyId()).isEqualTo("KEY1234567");
        assertThat(jwt.getPayload().getIssuer()).isEqualTo("TEAM123456");
        assertThat(jwt.getPayload().getSubject()).isEqualTo("com.moamap.ios");
        assertThat(jwt.getPayload().getAudience()).containsExactly("https://appleid.apple.com");
        assertThat(jwt.getPayload().getIssuedAt().toInstant()).isEqualTo(NOW);
        assertThat(jwt.getPayload().getExpiration().toInstant()).isEqualTo(NOW.plusSeconds(300));
    }

    @Test
    void 잘못된_개인키와_다른_곡선은_설정_오류로_거부한다() {
        for (String key : new String[] {"private-secret-bad", pem(Jwts.SIG.ES384.keyPair().build())}) {
            assertThatThrownBy(() -> new AppleClientSecretProvider(new AppleProperties("com.moamap.ios"),
                    new AppleTokenProperties("TEAM123456", "KEY1234567", key), Clock.systemUTC()))
                    .isInstanceOf(IllegalStateException.class).hasMessageNotContaining(key);
        }
        assertThat(properties().toString()).doesNotContain("BEGIN PRIVATE KEY");
    }
}
