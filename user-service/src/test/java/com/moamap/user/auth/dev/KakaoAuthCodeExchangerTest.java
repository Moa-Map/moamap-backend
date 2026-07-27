package com.moamap.user.auth.dev;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.moamap.user.auth.exception.InvalidOAuthTokenException;
import com.moamap.user.auth.oauth.KakaoProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KakaoAuthCodeExchangerTest {

    private static final String TOKEN_URI = "https://kauth.kakao.com/oauth/token";

    private static KakaoProperties properties() {
        return new KakaoProperties(0, null, null, "rest-key", "http://localhost/callback", TOKEN_URI);
    }

    @Test
    void code를_교환해_access_token을_반환한다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(TOKEN_URI))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"access_token\":\"kakao-token\"}", MediaType.APPLICATION_JSON));

        KakaoAuthCodeExchanger exchanger = new KakaoAuthCodeExchanger(builder, properties());

        assertThat(exchanger.exchange("auth-code")).isEqualTo("kakao-token");
        server.verify();
    }

    @Test
    void 교환_응답에_access_token이_없으면_예외를_던진다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(TOKEN_URI))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        KakaoAuthCodeExchanger exchanger = new KakaoAuthCodeExchanger(builder, properties());

        assertThatThrownBy(() -> exchanger.exchange("auth-code"))
                .isInstanceOf(InvalidOAuthTokenException.class);
    }

    @Test
    void 카카오_토큰_서버가_실패하면_예외를_던진다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(TOKEN_URI)).andRespond(withServerError());

        KakaoAuthCodeExchanger exchanger = new KakaoAuthCodeExchanger(builder, properties());

        assertThatThrownBy(() -> exchanger.exchange("auth-code"))
                .isInstanceOf(InvalidOAuthTokenException.class);
    }

    // --- 게이팅: dev-login.enabled 로만 빈이 등록되는지 ---

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(RestClientAutoConfiguration.class))
            .withBean(KakaoProperties.class, KakaoAuthCodeExchangerTest::properties)
            .withUserConfiguration(GatingConfig.class);

    @Test
    void dev_login이_비활성이면_교환기_빈이_없다() {
        contextRunner.withPropertyValues("dev-login.enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(KakaoAuthCodeExchanger.class));
    }

    @Test
    void dev_login이_활성이면_교환기_빈이_등록된다() {
        contextRunner.withPropertyValues("dev-login.enabled=true")
                .run(ctx -> assertThat(ctx).hasSingleBean(KakaoAuthCodeExchanger.class));
    }

    @Import(KakaoAuthCodeExchanger.class)
    static class GatingConfig {
    }
}
