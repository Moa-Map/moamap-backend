package com.moamap.user.auth.apple;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.moamap.user.user.entity.User;
import com.moamap.user.user.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientException;

/**
 * 탈퇴 회원의 Apple 토큰 폐기를 실제 DB로 확인한다. 성공해야 행이 지워지고, 실패하면 남아서 간격을 두고 다시 집히는지 본다.
 */
@DataJpaTest
@Import(AppleTokenRevocationProcessorTest.Config.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AppleTokenRevocationProcessorTest {

    private static final Instant NOW = Instant.parse("2026-10-06T00:00:00Z");
    private static final String CLIENT_ID = "com.moamap.ios";

    @Autowired private AppleTokenRevocationProcessor processor;
    @Autowired private AppleCredentialRepository credentials;
    @Autowired private UserRepository users;
    @Autowired private AppleTokenCipher cipher;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoBean private AppleTokenRevoker revoker;

    @AfterEach
    void cleanUp() {
        credentials.deleteAll();
        users.deleteAll();
    }

    @Test
    void 폐기에_성공하면_저장된_토큰_행을_지운다() {
        Long userId = appleUser("sub-1", "refresh-1", NOW);

        processor.revokeNow(userId);

        verify(revoker).revoke("refresh-1");
        assertThat(credentials.findById(userId)).isEmpty();
    }

    @Test
    void 폐기에_실패하면_행을_남기고_다음_시도_시각을_미룬다() {
        Long userId = appleUser("sub-1", "refresh-1", NOW);
        willThrow(new RestClientException("apple down")).given(revoker).revoke(anyString());

        processor.revokeNow(userId);

        assertThat(credentials.findById(userId)).hasValueSatisfying(credential -> {
            assertThat(credential.getRevokeAttempts()).isEqualTo(1);
            assertThat(credential.getNextRevokeAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
        });
    }

    @Test
    void 실패가_이어지면_재시도_간격을_두_배씩_늘린다() {
        Long userId = appleUser("sub-1", "refresh-1", NOW);
        willThrow(new RestClientException("apple down")).given(revoker).revoke(anyString());

        processor.revokeNow(userId);
        processor.revokeNow(userId);
        processor.revokeNow(userId);

        assertThat(credentials.findById(userId).orElseThrow().getNextRevokeAt())
                .isEqualTo(NOW.plus(Duration.ofMinutes(20)));
    }

    @Test
    void 배치는_시도할_때가_된_폐기_대기_행만_처리한다() {
        Long due = appleUser("sub-due", "refresh-due", NOW.minusSeconds(1));
        Long notYet = appleUser("sub-later", "refresh-later", NOW.plusSeconds(60));
        Long loggedIn = appleUser("sub-active", "refresh-active", null);

        processor.retryDue();

        verify(revoker).revoke("refresh-due");
        verify(revoker, never()).revoke("refresh-later");
        // 탈퇴하지 않은 회원의 토큰은 건드리지 않는다.
        verify(revoker, never()).revoke("refresh-active");
        assertThat(credentials.findById(due)).isEmpty();
        assertThat(credentials.findById(notYet)).isPresent();
        assertThat(credentials.findById(loggedIn)).isPresent();
    }

    @Test
    void 탈퇴하지_않은_회원은_바로_폐기_대상이_아니다() {
        Long userId = appleUser("sub-1", "refresh-1", null);

        processor.revokeNow(userId);

        verify(revoker, never()).revoke(anyString());
        assertThat(credentials.findById(userId)).isPresent();
    }

    @Test
    void 횟수를_다_쓴_행은_배치가_더_집지_않는다() {
        Long userId = appleUser("sub-1", "refresh-1", NOW.minusSeconds(1));
        willThrow(new RestClientException("apple down")).given(revoker).revoke(anyString());
        for (int i = 0; i < AppleTokenRevocationProcessor.MAX_ATTEMPTS; i++) {
            processor.revokeNow(userId);
        }
        // 다음 시도 시각과 상관없이 횟수로 멈춰야 한다.
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                credentials.findById(userId).orElseThrow().requestRevoke(NOW.minusSeconds(1)));

        processor.retryDue();

        verify(revoker, times(AppleTokenRevocationProcessor.MAX_ATTEMPTS)).revoke(anyString());
        assertThat(credentials.findById(userId)).hasValueSatisfying(credential ->
                assertThat(credential.getRevokeAttempts()).isEqualTo(AppleTokenRevocationProcessor.MAX_ATTEMPTS));
    }

    @Test
    void 복호화할_수_없는_토큰도_실패로_세고_다시_시도한다() {
        Long userId = new TransactionTemplate(transactionManager).execute(status -> {
            User user = users.save(User.createSocialUser("apple", "sub-1", "사용자", null, null));
            // 다른 회원 ID로 암호화한 값은 AAD가 달라 복호화되지 않는다.
            AppleCredential credential = new AppleCredential(user, CLIENT_ID, cipher.encrypt(999L, CLIENT_ID, "x"));
            credential.requestRevoke(NOW);
            credentials.save(credential);
            return user.getId();
        });

        processor.revokeNow(userId);

        verify(revoker, never()).revoke(anyString());
        assertThat(credentials.findById(userId).orElseThrow().getRevokeAttempts()).isEqualTo(1);
    }

    /** revokeRequestedAt이 null이면 탈퇴하지 않은(로그인 중인) 회원이다. */
    private Long appleUser(String subject, String refreshToken, Instant revokeRequestedAt) {
        // 자격증명은 회원 행을 공유 키로 쓰므로(@MapsId) 같은 영속성 컨텍스트에서 함께 만든다.
        return new TransactionTemplate(transactionManager).execute(status -> {
            User user = users.save(User.createSocialUser("apple", subject, "사용자", null, null));
            AppleCredential credential = new AppleCredential(user, CLIENT_ID,
                    cipher.encrypt(user.getId(), CLIENT_ID, refreshToken));
            if (revokeRequestedAt != null) {
                credential.requestRevoke(revokeRequestedAt);
            }
            credentials.save(credential);
            return user.getId();
        });
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {
        @Bean
        AppleTokenCipher appleTokenCipher() {
            String key = Base64.getEncoder().encodeToString(new byte[32]);
            return new AppleTokenCipher(new AppleCredentialProperties("v1", Map.of("v1", key)));
        }

        @Bean
        AppleTokenRevocationProcessor appleTokenRevocationProcessor(AppleCredentialRepository credentials,
                AppleTokenCipher cipher, AppleTokenRevoker revoker) {
            return new AppleTokenRevocationProcessor(credentials, cipher, revoker, Clock.fixed(NOW, ZoneOffset.UTC));
        }
    }
}
