package com.moamap.user.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.moamap.user.auth.exception.InvalidOAuthTokenException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KakaoOAuthClientTest {

    private static final String TOKEN_INFO_URI = "http://localhost/v1/user/access_token_info";
    private static final String USER_INFO_URI = "http://localhost/v2/user/me";

    @Test
    void 카카오_토큰으로_사용자정보를_가져온다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KakaoOAuthClient client = new KakaoOAuthClient(
                builder, new KakaoProperties(12345L, TOKEN_INFO_URI, USER_INFO_URI));

        server.expect(requestTo(TOKEN_INFO_URI))
                .andRespond(withSuccess("{\"id\":111,\"app_id\":12345}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(USER_INFO_URI))
                .andRespond(withSuccess("""
                        {"id":111,"kakao_account":{"email":"a@b.com",
                         "profile":{"nickname":"길동","profile_image_url":"http://img"}}}
                        """, MediaType.APPLICATION_JSON));

        OAuthUserInfo info = client.getUserInfo("kakao-access-token");

        assertThat(info.provider()).isEqualTo("kakao");
        assertThat(info.providerId()).isEqualTo("111");
        assertThat(info.nickname()).isEqualTo("길동");
        assertThat(info.email()).isEqualTo("a@b.com");
        assertThat(info.profileImageUrl()).isEqualTo("http://img");
    }

    @Test
    void 우리앱_토큰이_아니면_예외를_던진다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KakaoOAuthClient client = new KakaoOAuthClient(
                builder, new KakaoProperties(12345L, TOKEN_INFO_URI, USER_INFO_URI));

        server.expect(requestTo(TOKEN_INFO_URI))
                .andRespond(withSuccess("{\"id\":111,\"app_id\":99999}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.getUserInfo("other-app-token"))
                .isInstanceOf(InvalidOAuthTokenException.class);
    }

    @Test
    void 사용자정보_응답이_비면_예외를_던진다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KakaoOAuthClient client = new KakaoOAuthClient(
                builder, new KakaoProperties(12345L, TOKEN_INFO_URI, USER_INFO_URI));

        server.expect(requestTo(TOKEN_INFO_URI))
                .andRespond(withSuccess("{\"id\":111,\"app_id\":12345}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(USER_INFO_URI))
                .andRespond(withSuccess()); // 빈 2xx 응답

        assertThatThrownBy(() -> client.getUserInfo("kakao-access-token"))
                .isInstanceOf(InvalidOAuthTokenException.class);
    }
}
