package com.moamap.user.auth.apple;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Clock;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

class AppleTokenRevokerTest {

    private MockRestServiceServer server;
    private AppleTokenRevoker revoker;

    @BeforeEach
    void setUp() {
        var clock = Clock.fixed(AppleClientSecretProviderTest.NOW, ZoneOffset.UTC);
        var props = new AppleProperties("com.moamap.ios");
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        var secrets = new AppleClientSecretProvider(props, AppleClientSecretProviderTest.properties(), clock);
        revoker = new AppleTokenRevoker(builder.build(), props, secrets);
    }

    @Test
    void 리프레시_토큰_폐기를_Apple에_요청한다() {
        server.expect(requestTo(AppleTokenRevoker.REVOKE_URI))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("client_id=com.moamap.ios"),
                        org.hamcrest.Matchers.containsString("token=apple-refresh"),
                        org.hamcrest.Matchers.containsString("token_type_hint=refresh_token"),
                        org.hamcrest.Matchers.containsString("client_secret="))))
                .andRespond(withSuccess());

        revoker.revoke("apple-refresh");

        server.verify();
    }

    @Test
    void Apple이_거절하면_예외를_던져_호출부가_기록하게_한다() {
        server.expect(requestTo(AppleTokenRevoker.REVOKE_URI)).andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> revoker.revoke("apple-refresh")).isInstanceOf(RestClientException.class);
    }
}
