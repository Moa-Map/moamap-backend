package com.moamap.user.auth.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.moamap.user.auth.service.AuthService;
import com.moamap.user.auth.service.GoogleLoginService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

class GoogleAuthConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(GoogleAuthConfiguration.class)
            .withBean(RestClient.Builder.class, RestClient::builder)
            .withBean(AuthService.class, () -> mock(AuthService.class));

    @Test
    void 기본은_비활성화이며_Google_설정이_없어도_기동한다() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(GooglePublicKeyProvider.class);
            assertThat(context).doesNotHaveBean(GoogleIdTokenVerifier.class);
            assertThat(context).doesNotHaveBean(GoogleLoginService.class);
        });
    }

    @Test
    void enabled가_false이면_clientIds가_있어도_빈을_등록하지_않는다() {
        runner.withPropertyValues("google.enabled=false", "google.client-ids=web-id")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(GoogleIdTokenVerifier.class);
                });
    }

    @Test
    void 활성화할_때_clientIds가_없으면_기동을_거부한다() {
        runner.withPropertyValues("google.enabled=true").run(context -> assertThat(context).hasFailed());
    }

    @Test
    void 활성화할_때_clientIds가_빈_문자열이면_기동을_거부한다() {
        runner.withPropertyValues("google.enabled=true", "google.client-ids=")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void 활성화하고_clientIds를_설정하면_인증_컴포넌트를_등록한다() {
        runner.withPropertyValues("google.enabled=true", "google.client-ids=web-id,ios-id")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(GooglePublicKeyProvider.class);
                    assertThat(context).hasSingleBean(GoogleIdTokenVerifier.class);
                    assertThat(context).hasSingleBean(GoogleLoginService.class);
                    assertThat(context.getBean(GoogleProperties.class).clientIds())
                            .containsExactly("web-id", "ios-id");
                });
    }
}
