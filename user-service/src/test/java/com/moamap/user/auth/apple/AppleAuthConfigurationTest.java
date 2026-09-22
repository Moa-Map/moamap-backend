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
            .withBean(RestClient.Builder.class, RestClient::builder);

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
    void 활성화하고_clientId를_설정하면_인증_컴포넌트를_등록한다() {
        runner.withPropertyValues("apple.enabled=true", "apple.client-id=com.moamap.ios")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AppleNonceController.class);
                    assertThat(context).hasSingleBean(AppleIdentityTokenVerifier.class);
                });
    }
}
