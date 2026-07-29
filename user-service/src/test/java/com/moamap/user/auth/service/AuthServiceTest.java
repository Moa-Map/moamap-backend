package com.moamap.user.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.moamap.user.auth.dto.TokenResponse;
import com.moamap.user.auth.exception.RefreshTokenNotFoundException;
import com.moamap.user.auth.jwt.JwtProperties;
import com.moamap.user.auth.jwt.JwtProvider;
import com.moamap.user.auth.oauth.OAuthClient;
import com.moamap.user.auth.oauth.OAuthUserInfo;
import com.moamap.user.outbox.OutboxRecorder;
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
    @Mock private OutboxRecorder outboxRecorder;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                oAuthClient, userRepository, refreshTokenStore, jwtProvider,
                new JwtProperties("secret-key-min-32-bytes-long-for-test!!", 30, 14),
                outboxRecorder);
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
        assertThat(response.refreshTokenExpiresIn()).isEqualTo(Duration.ofDays(14).getSeconds());
        assertThat(response.isNewUser()).isTrue();
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
        assertThat(response.isNewUser()).isFalse();
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void 유효한_리프레시_토큰으로_재발급하면_기존_토큰을_회전한다() {
        User user = User.createSocialUser("kakao", "111", "길동", null, null);
        ReflectionTestUtils.setField(user, "id", 5L);
        given(refreshTokenStore.findUserId("old-refresh")).willReturn(Optional.of(5L));
        given(userRepository.findById(5L)).willReturn(Optional.of(user));
        given(jwtProvider.createAccessToken(5L, Role.USER)).willReturn("new-access");
        given(jwtProvider.getAccessTokenExpiresInSeconds()).willReturn(1800L);

        TokenResponse response = authService.refresh("old-refresh");

        assertThat(response.accessToken()).isEqualTo("new-access");
        assertThat(response.refreshToken()).isNotEqualTo("old-refresh");
        verify(refreshTokenStore).delete("old-refresh");
        verify(refreshTokenStore).save(anyString(), eq(5L), any(Duration.class));
    }

    @Test
    void 없거나_만료된_리프레시_토큰이면_예외를_던진다() {
        given(refreshTokenStore.findUserId("nope")).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("nope"))
                .isInstanceOf(RefreshTokenNotFoundException.class);
    }

    @Test
    void 로그아웃하면_해당_리프레시_토큰을_삭제한다() {
        authService.logout("some-refresh");

        verify(refreshTokenStore).delete("some-refresh");
    }
}
