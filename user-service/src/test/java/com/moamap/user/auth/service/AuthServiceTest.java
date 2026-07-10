package com.moamap.user.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.moamap.user.auth.dto.TokenResponse;
import com.moamap.user.auth.jwt.JwtProperties;
import com.moamap.user.auth.jwt.JwtProvider;
import com.moamap.user.auth.oauth.OAuthClient;
import com.moamap.user.auth.oauth.OAuthUserInfo;
import com.moamap.user.refreshtoken.RefreshTokenStore;
import com.moamap.user.user.entity.Role;
import com.moamap.user.user.entity.User;
import com.moamap.user.user.repository.UserRepository;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private OAuthClient oAuthClient;
    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenStore refreshTokenStore;
    @Mock private JwtProvider jwtProvider;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                oAuthClient, userRepository, refreshTokenStore, jwtProvider,
                new JwtProperties("secret-key-min-32-bytes-long-for-test!!", 30, 14));
    }

    @Test
    void 신규_사용자는_가입_후_토큰을_발급받는다() {
        given(oAuthClient.getUserInfo("kakao-token"))
                .willReturn(new OAuthUserInfo("kakao", "111", "길동", "a@b.com", "http://img"));
        given(userRepository.findByProviderAndProviderId("kakao", "111"))
                .willReturn(Optional.empty());
        given(userRepository.save(any(User.class))).willAnswer(inv -> {
            User u = inv.getArgument(0);
            ReflectionTestUtils.setField(u, "id", 1L);
            return u;
        });
        given(jwtProvider.createAccessToken(1L, Role.USER)).willReturn("access-jwt");
        given(jwtProvider.getAccessTokenExpiresInSeconds()).willReturn(1800L);

        TokenResponse response = authService.login("kakao-token");

        assertThat(response.accessToken()).isEqualTo("access-jwt");
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(1800L);
        verify(refreshTokenStore).save(anyString(), eq(1L), any(Duration.class));
    }

    @Test
    void 기존_사용자는_가입없이_토큰을_발급받는다() {
        User existing = User.createSocialUser("kakao", "111", "길동", "a@b.com", null);
        ReflectionTestUtils.setField(existing, "id", 7L);
        given(oAuthClient.getUserInfo("kakao-token"))
                .willReturn(new OAuthUserInfo("kakao", "111", "길동", "a@b.com", null));
        given(userRepository.findByProviderAndProviderId("kakao", "111"))
                .willReturn(Optional.of(existing));
        given(jwtProvider.createAccessToken(7L, Role.USER)).willReturn("access-jwt");
        given(jwtProvider.getAccessTokenExpiresInSeconds()).willReturn(1800L);

        TokenResponse response = authService.login("kakao-token");

        assertThat(response.accessToken()).isEqualTo("access-jwt");
        verify(userRepository, never()).save(any(User.class));
    }
}
