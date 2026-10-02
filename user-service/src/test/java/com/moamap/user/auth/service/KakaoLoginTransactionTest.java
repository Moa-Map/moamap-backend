package com.moamap.user.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;

import com.moamap.user.auth.dto.TokenResponse;
import com.moamap.user.auth.exception.InvalidOAuthTokenException;
import com.moamap.user.auth.jwt.JwtProperties;
import com.moamap.user.auth.jwt.JwtProvider;
import com.moamap.user.auth.oauth.KakaoOAuthClient;
import com.moamap.user.auth.oauth.OAuthUserInfo;
import com.moamap.user.outbox.OutboxEventRepository;
import com.moamap.user.outbox.OutboxRecorder;
import com.moamap.user.refreshtoken.RefreshTokenStore;
import com.moamap.user.user.entity.User;
import com.moamap.user.user.repository.UserRepository;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@DataJpaTest
@Import({KakaoLoginService.class, AuthService.class, OutboxRecorder.class,
        JwtProvider.class, JacksonAutoConfiguration.class})
@EnableConfigurationProperties(JwtProperties.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class KakaoLoginTransactionTest {

    @Autowired private KakaoLoginService kakaoLoginService;
    @Autowired private UserRepository userRepository;
    @Autowired private OutboxEventRepository outboxEventRepository;
    @Autowired private JwtProvider jwtProvider;
    @MockitoBean private KakaoOAuthClient kakaoOAuthClient;
    @MockitoBean private RefreshTokenStore refreshTokenStore;

    @AfterEach
    void cleanUp() {
        outboxEventRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void 카카오_인증은_트랜잭션_밖에서_수행하고_회원과_가입이벤트는_함께_저장한다() {
        given(kakaoOAuthClient.getUserInfo("kakao-token")).willAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return new OAuthUserInfo("kakao", "111", "길동", "a@b.com", "http://img");
        });

        TokenResponse response = kakaoLoginService.login("kakao-token");

        User saved = userRepository.findById(response.userId()).orElseThrow();
        assertThat(saved.getNickname()).isEqualTo("길동");
        assertThat(saved.getEmail()).isEqualTo("a@b.com");
        assertThat(saved.getProfileImageUrl()).isEqualTo("http://img");
        assertThat(saved.getLastLoginAt()).isNotNull();
        assertThat(response.isNewUser()).isTrue();
        assertThat(jwtProvider.getUserId(response.accessToken())).isEqualTo(saved.getId());
        assertThat(outboxEventRepository.findAll()).singleElement().satisfies(event -> {
            assertThat(event.getAggregateId()).isEqualTo(saved.getId().toString());
            assertThat(event.getEventType()).isEqualTo("user.registered");
            assertThat(event.getPayload()).contains("\"userId\":" + saved.getId());
        });
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void 닉네임이_없으면_기존_카카오_기본값을_저장한다(String nickname) {
        given(kakaoOAuthClient.getUserInfo("kakao-token"))
                .willReturn(new OAuthUserInfo("kakao", "111", nickname, null, null));

        TokenResponse response = kakaoLoginService.login("kakao-token");

        assertThat(userRepository.findById(response.userId()).orElseThrow().getNickname())
                .isEqualTo("kakao_111");
    }

    @Test
    void 재로그인은_수정한_닉네임을_유지하고_가입이벤트를_추가하지_않는다() {
        User existing = userRepository.save(User.createSocialUser("kakao", "111", "내닉네임", null, null));
        given(kakaoOAuthClient.getUserInfo("kakao-token"))
                .willReturn(new OAuthUserInfo("kakao", "111", "카카오닉네임", null, null));

        TokenResponse response = kakaoLoginService.login("kakao-token");

        assertThat(response.userId()).isEqualTo(existing.getId());
        assertThat(response.isNewUser()).isFalse();
        User saved = userRepository.findById(existing.getId()).orElseThrow();
        assertThat(saved.getNickname()).isEqualTo("내닉네임");
        assertThat(saved.getLastLoginAt()).isNotNull();
        assertThat(outboxEventRepository.count()).isZero();
    }

    @Test
    void 토큰_저장이_실패하면_회원과_가입이벤트를_함께_롤백한다() {
        given(kakaoOAuthClient.getUserInfo("kakao-token"))
                .willReturn(new OAuthUserInfo("kakao", "111", "길동", null, null));
        doThrow(new IllegalStateException("Redis unavailable"))
                .when(refreshTokenStore).save(anyString(), anyLong(), any(Duration.class));

        assertThatThrownBy(() -> kakaoLoginService.login("kakao-token"))
                .isInstanceOf(IllegalStateException.class);

        assertThat(userRepository.count()).isZero();
        assertThat(outboxEventRepository.count()).isZero();
    }

    @Test
    void 카카오_인증이_실패하면_회원과_토큰을_만들지_않는다() {
        given(kakaoOAuthClient.getUserInfo("bad-token"))
                .willThrow(new InvalidOAuthTokenException("bad token"));

        assertThatThrownBy(() -> kakaoLoginService.login("bad-token"))
                .isInstanceOf(InvalidOAuthTokenException.class);

        assertThat(userRepository.count()).isZero();
        assertThat(outboxEventRepository.count()).isZero();
        verifyNoInteractions(refreshTokenStore);
    }
}
