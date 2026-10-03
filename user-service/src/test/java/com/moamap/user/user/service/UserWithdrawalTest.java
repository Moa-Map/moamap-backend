package com.moamap.user.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.moamap.user.auth.apple.AppleCredential;
import com.moamap.user.auth.apple.AppleCredentialProperties;
import com.moamap.user.auth.apple.AppleCredentialRepository;
import com.moamap.user.auth.apple.AppleTokenCipher;
import com.moamap.user.auth.dto.TokenResponse;
import com.moamap.user.auth.exception.RefreshTokenNotFoundException;
import com.moamap.user.auth.jwt.JwtProperties;
import com.moamap.user.auth.jwt.JwtProvider;
import com.moamap.user.auth.oauth.OAuthUserInfo;
import com.moamap.user.auth.service.AuthService;
import com.moamap.user.event.UserWithdrawnEvent;
import com.moamap.user.outbox.OutboxEvent;
import com.moamap.user.outbox.OutboxEventRepository;
import com.moamap.user.outbox.OutboxRecorder;
import com.moamap.user.refreshtoken.RefreshTokenStore;
import com.moamap.user.user.entity.User;
import com.moamap.user.user.exception.UserNotFoundException;
import com.moamap.user.user.repository.UserRepository;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 탈퇴의 DB 부분을 실제 DB로 확인한다. 익명화·이벤트 기록·Apple 토큰 회수와,
 * 탈퇴 뒤 같은 소셜 계정의 재가입과 남은 리프레시 토큰 차단까지 한 흐름으로 본다.
 */
@DataJpaTest
@Import({UserWithdrawalWriter.class, AuthService.class, OutboxRecorder.class, JwtProvider.class,
        JacksonAutoConfiguration.class, UserWithdrawalTest.Config.class})
@EnableConfigurationProperties(JwtProperties.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class UserWithdrawalTest {

    private static final String CLIENT_ID = "com.moamap.ios";

    @Autowired private UserWithdrawalWriter withdrawalWriter;
    @Autowired private AuthService authService;
    @Autowired private UserRepository userRepository;
    @Autowired private OutboxEventRepository outboxEventRepository;
    @Autowired private AppleCredentialRepository appleCredentialRepository;
    @Autowired private AppleTokenCipher cipher;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoBean private RefreshTokenStore refreshTokenStore;

    @AfterEach
    void cleanUp() {
        appleCredentialRepository.deleteAll();
        outboxEventRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void 탈퇴하면_행은_남기고_개인정보를_지운다() {
        Long userId = kakaoUser("3412345678").getId();

        withdrawalWriter.withdraw(userId);

        User withdrawn = userRepository.findById(userId).orElseThrow();
        assertThat(withdrawn.isWithdrawn()).isTrue();
        assertThat(withdrawn.getNickname()).isEqualTo(User.WITHDRAWN_NICKNAME);
        assertThat(withdrawn.getEmail()).isNull();
        assertThat(withdrawn.getProfileImageUrl()).isNull();
        assertThat(withdrawn.getIntroduction()).isNull();
        // 소셜 회원번호도 개인 식별 정보라 남기지 않는다.
        assertThat(withdrawn.getProviderId()).doesNotContain("3412345678").isEqualTo("withdrawn:" + userId);
    }

    @Test
    void 탈퇴_이벤트를_같은_트랜잭션에_기록한다() {
        Long userId = kakaoUser("111").getId();

        withdrawalWriter.withdraw(userId);

        List<OutboxEvent> events = outboxEventRepository.findAll();
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getEventType()).isEqualTo(UserWithdrawnEvent.TYPE);
            assertThat(event.getAggregateId()).isEqualTo(String.valueOf(userId));
            assertThat(event.getPayload()).contains("\"userId\":" + userId);
            // 이벤트에는 개인정보를 싣지 않는다.
            assertThat(event.getPayload()).doesNotContain("user@example.com", "모아맵러");
        });
    }

    @Test
    void 카카오_회원은_폐기할_Apple_토큰이_없다() {
        Long userId = kakaoUser("111").getId();

        WithdrawnUser withdrawn = withdrawalWriter.withdraw(userId);

        assertThat(withdrawn.userId()).isEqualTo(userId);
        assertThat(withdrawn.appleRefreshToken()).isNull();
    }

    @Test
    void 이미_탈퇴한_회원은_다시_탈퇴할_수_없다() {
        Long userId = kakaoUser("111").getId();
        withdrawalWriter.withdraw(userId);

        assertThatThrownBy(() -> withdrawalWriter.withdraw(userId)).isInstanceOf(UserNotFoundException.class);
        // 두 번째 요청이 이벤트를 또 남기면 안 된다.
        assertThat(outboxEventRepository.count()).isEqualTo(1);
    }

    @Test
    void 없는_회원은_탈퇴할_수_없다() {
        assertThatThrownBy(() -> withdrawalWriter.withdraw(999_999L)).isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void Apple_회원이면_토큰을_꺼내고_저장된_행은_지운다() {
        // 자격증명은 회원 행을 공유 키로 쓰므로(@MapsId) 같은 영속성 컨텍스트에서 함께 만든다.
        Long userId = new TransactionTemplate(transactionManager).execute(status -> {
            User user = userRepository.save(User.createSocialUser("apple", "apple-sub", "사용자", null, null));
            appleCredentialRepository.save(new AppleCredential(user, CLIENT_ID,
                    cipher.encrypt(user.getId(), CLIENT_ID, "apple-refresh-secret")));
            return user.getId();
        });

        WithdrawnUser withdrawn = withdrawalWriter.withdraw(userId);

        assertThat(withdrawn.appleRefreshToken()).isEqualTo("apple-refresh-secret");
        assertThat(withdrawn.toString()).doesNotContain("apple-refresh-secret");
        assertThat(appleCredentialRepository.findById(userId)).isEmpty();
    }

    @Test
    void 탈퇴한_소셜_계정으로_다시_로그인하면_새_회원으로_가입된다() {
        Long oldUserId = kakaoUser("3412345678").getId();
        withdrawalWriter.withdraw(oldUserId);

        TokenResponse response = authService.login(new OAuthUserInfo("kakao", "3412345678", "다시온사람", null, null));

        assertThat(response.isNewUser()).isTrue();
        assertThat(response.userId()).isNotEqualTo(oldUserId);
        // 옛 계정이 살아나지 않는다.
        assertThat(userRepository.findById(oldUserId).orElseThrow().isWithdrawn()).isTrue();
    }

    @Test
    void 탈퇴한_회원의_리프레시_토큰으로는_재발급되지_않는다() {
        Long userId = kakaoUser("111").getId();
        withdrawalWriter.withdraw(userId);
        given(refreshTokenStore.findUserId("leftover-token")).willReturn(Optional.of(userId));

        assertThatThrownBy(() -> authService.refresh("leftover-token"))
                .isInstanceOf(RefreshTokenNotFoundException.class);
    }

    @Test
    void 탈퇴_직전에_회원을_읽어_둔_요청은_탈퇴를_되돌리지_못한다() {
        Long userId = kakaoUser("3412345678").getId();
        ExecutorService otherRequest = Executors.newSingleThreadExecutor();
        try {
            // 로그인 요청이 회원을 읽은 사이에 탈퇴가 커밋되고, 그 뒤 로그인이 마지막 접속 시각을 저장하는 순서다.
            assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                User readBeforeWithdrawal = userRepository.findById(userId).orElseThrow();
                awaitWithdrawal(otherRequest, userId);
                readBeforeWithdrawal.updateLastLogin(Instant.now());
            })).isInstanceOf(ObjectOptimisticLockingFailureException.class);
        } finally {
            otherRequest.shutdownNow();
        }

        // JPA는 모든 컬럼을 다시 쓰므로, 막지 않으면 deleted_at과 이메일이 탈퇴 전 값으로 돌아간다.
        User reloaded = userRepository.findById(userId).orElseThrow();
        assertThat(reloaded.isWithdrawn()).isTrue();
        assertThat(reloaded.getEmail()).isNull();
        assertThat(reloaded.getProviderId()).isEqualTo("withdrawn:" + userId);
    }

    private void awaitWithdrawal(ExecutorService executor, Long userId) {
        try {
            executor.submit(() -> withdrawalWriter.withdraw(userId)).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private User kakaoUser(String providerId) {
        User user = userRepository.save(User.createSocialUser("kakao", providerId, "모아맵러", "user@example.com", null));
        user.updateProfile(null, "https://photos.example.com/profiles/" + user.getId() + "/a.jpg", null, "자기소개");
        return userRepository.save(user);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {
        @Bean
        AppleTokenCipher appleTokenCipher() {
            String key = Base64.getEncoder().encodeToString(new byte[32]);
            return new AppleTokenCipher(new AppleCredentialProperties("v1", Map.of("v1", key)));
        }
    }
}
