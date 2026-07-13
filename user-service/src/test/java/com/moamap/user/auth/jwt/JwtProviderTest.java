package com.moamap.user.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import com.moamap.user.user.entity.Role;
import org.junit.jupiter.api.Test;

class JwtProviderTest {

    private static final String SECRET = "unit-test-jwt-secret-key-min-32-bytes-long!!";

    @Test
    void 액세스_토큰을_만들고_검증하면_userId와_role을_복원한다() {
        JwtProvider provider = new JwtProvider(new JwtProperties(SECRET, 30, 14));

        String token = provider.createAccessToken(42L, Role.USER);

        assertThat(provider.validate(token)).isTrue();
        assertThat(provider.getUserId(token)).isEqualTo(42L);
        assertThat(provider.getRole(token)).isEqualTo(Role.USER);
    }

    @Test
    void 만료된_토큰은_검증에_실패한다() {
        JwtProvider provider = new JwtProvider(new JwtProperties(SECRET, -1, 14)); // 이미 만료

        String token = provider.createAccessToken(42L, Role.USER);

        assertThat(provider.validate(token)).isFalse();
    }

    @Test
    void 변조된_토큰은_검증에_실패한다() {
        JwtProvider provider = new JwtProvider(new JwtProperties(SECRET, 30, 14));

        String token = provider.createAccessToken(42L, Role.USER);

        assertThat(provider.validate(token + "x")).isFalse();
    }
}
