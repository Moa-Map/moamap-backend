package com.moamap.user.auth.apple;

import static org.assertj.core.api.Assertions.*;
import com.moamap.common.exception.BusinessException;
import com.moamap.user.user.entity.User;
import com.moamap.user.user.repository.UserRepository;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@Import(AppleCredentialStoreTest.Config.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AppleCredentialStoreTest {
    @Autowired private AppleCredentialStore store;
    @Autowired private AppleCredentialRepository credentials;
    @Autowired private UserRepository users;
    @Autowired private AppleTokenCipher cipher;
    @Autowired private PlatformTransactionManager transactionManager;
    private TransactionTemplate tx;

    @BeforeEach void setUp() { tx = new TransactionTemplate(transactionManager); }
    @AfterEach void cleanup() { credentials.deleteAll(); users.deleteAll(); }

    @Test
    void 회원과_같은_트랜잭션에서_암호화된_인증정보를_저장한다() {
        Long id = tx.execute(status -> {
            User user = users.save(User.createSocialUser("apple", "apple-user", "사용자", null, null));
            store.save(user.getId(), "refresh-secret");
            return user.getId();
        });
        var saved = credentials.findById(id).orElseThrow();
        assertThat(saved.getClientId()).isEqualTo("com.moamap.ios");
        assertThat(saved.getEncryptedRefreshToken()).doesNotContain("refresh-secret");
        assertThat(saved.getKeyVersion()).isEqualTo("v1");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(decrypt(saved)).isEqualTo("refresh-secret");
    }

    @Test
    void 새_토큰이_없으면_기존값을_유지하고_있으면_교체한다() {
        Long id = create("old-token");
        String oldCiphertext = credentials.findById(id).orElseThrow().getEncryptedRefreshToken();
        tx.executeWithoutResult(status -> store.save(id, null));
        assertThat(credentials.findById(id).orElseThrow().getEncryptedRefreshToken()).isEqualTo(oldCiphertext);
        tx.executeWithoutResult(status -> store.save(id, "new-token"));
        assertThat(credentials.count()).isEqualTo(1);
        assertThat(decrypt(credentials.findById(id).orElseThrow())).isEqualTo("new-token");
    }

    @Test
    void 기존값도_새_토큰도_없으면_가입을_롤백한다() {
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            User user = users.save(User.createSocialUser("apple", "apple-user", "사용자", null, null));
            store.save(user.getId(), null);
        })).isInstanceOf(BusinessException.class);
        assertThat(users.count()).isZero();
        assertThat(credentials.count()).isZero();
    }

    @Test
    void 저장_후_상위_작업이_실패하면_회원과_인증정보를_함께_롤백한다() {
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            User user = users.save(User.createSocialUser("apple", "apple-user", "사용자", null, null));
            store.save(user.getId(), "refresh");
            throw new IllegalStateException("later operation failed");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(users.count()).isZero();
        assertThat(credentials.count()).isZero();
    }

    @Test
    void 트랜잭션_밖에서는_저장을_거부한다() {
        assertThatThrownBy(() -> store.save(1L, "refresh")).isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void 카카오_회원에게_Apple_인증정보를_저장하지_않는다() {
        User user = users.save(User.createSocialUser("kakao", "111", "사용자", null, null));
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> store.save(user.getId(), "refresh")))
                .isInstanceOf(BusinessException.class);
        assertThat(credentials.count()).isZero();
    }

    @Test
    void 같은_회원의_동시_최초저장은_하나의_행으로_직렬화된다() throws Exception {
        Long id = users.save(User.createSocialUser("apple", "apple-user", "사용자", null, null)).getId();
        var barrier = new java.util.concurrent.CyclicBarrier(2);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> concurrentSave(id, "first-token", barrier));
            var second = executor.submit(() -> concurrentSave(id, "second-token", barrier));
            first.get(10, java.util.concurrent.TimeUnit.SECONDS);
            second.get(10, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(credentials.count()).isEqualTo(1);
            assertThat(decrypt(credentials.findById(id).orElseThrow())).isIn("first-token", "second-token");
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS);
        }
    }

    private void concurrentSave(Long id, String token, java.util.concurrent.CyclicBarrier barrier) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            try {
                barrier.await(5, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            store.save(id, token);
        });
    }

    private Long create(String token) {
        return tx.execute(status -> {
            User user = users.save(User.createSocialUser("apple", "apple-user", "사용자", null, null));
            store.save(user.getId(), token);
            return user.getId();
        });
    }

    private String decrypt(AppleCredential saved) {
        return cipher.decrypt(saved.getUserId(), saved.getClientId(),
                new AppleTokenCipher.EncryptedToken(saved.getEncryptedRefreshToken(), saved.getKeyVersion()));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {
        @Bean AppleTokenCipher cipher() {
            return new AppleTokenCipher(new AppleCredentialProperties("v1", Map.of("v1", AppleTokenCipherTest.KEY_V1)));
        }
        @Bean AppleCredentialStore store(UserRepository users, AppleCredentialRepository credentials, AppleTokenCipher cipher) {
            return new AppleCredentialStore(users, credentials, cipher, new AppleProperties("com.moamap.ios"));
        }
    }
}
