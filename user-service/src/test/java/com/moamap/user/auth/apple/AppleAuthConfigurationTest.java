package com.moamap.user.auth.apple;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.client.RestClient;

class AppleAuthConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(AppleAuthConfiguration.class, AppleNonceController.class)
            .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
            .withBean(RestClient.Builder.class, RestClient::builder)
            .withBean(com.fasterxml.jackson.databind.ObjectMapper.class, com.fasterxml.jackson.databind.ObjectMapper::new)
            .withBean(com.moamap.user.user.repository.UserRepository.class,
                    () -> mock(com.moamap.user.user.repository.UserRepository.class))
            .withBean(AppleCredentialRepository.class, () -> mock(AppleCredentialRepository.class));

    @Test
    void 기본은_비활성화이며_Apple_설정이_없어도_기동한다() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(AppleNonceController.class);
            assertThat(context).doesNotHaveBean(AppleIdentityTokenVerifier.class);
        });
    }

    @Test
    void 활성화할_때_clientId가_없으면_기동을_거부한다() {
        runner.withPropertyValues("apple.enabled=true").run(context -> assertThat(context).hasFailed());
    }

    @Test
    void 앱_ID만_있고_서명과_암호화_키가_없으면_활성화하지_않는다() {
        runner.withPropertyValues("apple.enabled=true", "apple.client-id=com.moamap.ios")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void 활성화하고_필수키를_설정하면_인증_컴포넌트를_등록한다() {
        runner.withPropertyValues("apple.enabled=true", "apple.client-id=com.moamap.ios",
                        "apple.token.team-id=TEAM123456", "apple.token.key-id=KEY1234567",
                        "apple.token.private-key=" + AppleClientSecretProviderTest.pem(AppleClientSecretProviderTest.KEY),
                        "apple.credentials.active-key-version=v1", "apple.credentials.keys.v1=" + AppleTokenCipherTest.KEY_V1)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AppleNonceController.class);
                    assertThat(context).hasSingleBean(AppleIdentityTokenVerifier.class);
                    assertThat(context).hasSingleBean(AppleTokenExchanger.class);
                    assertThat(context).hasSingleBean(AppleCredentialStore.class);
                });
    }
}
