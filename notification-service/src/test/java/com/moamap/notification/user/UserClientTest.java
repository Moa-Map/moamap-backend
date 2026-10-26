package com.moamap.notification.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.moamap.notification.user.dto.UserProfileResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class UserClientTest {

    private static final String BASE_URL = "http://user-service";

    private MockRestServiceServer server;
    private UserClient userClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        userClient = new UserClient(builder, new UserServiceProperties(BASE_URL));
    }

    @Test
    void 여러_회원의_프로필을_한_번에_가져온다() {
        server.expect(requestTo(BASE_URL + "/api/v1/users/profiles?ids=3"))
                .andRespond(withSuccess("""
                        {"success":true,"data":[{"id":3,"nickname":"모아","profileImageUrl":null}]}
                        """, MediaType.APPLICATION_JSON));

        UserProfiles profiles = userClient.findProfiles(List.of(3L));

        assertThat(profiles.isAvailable()).isTrue();
        assertThat(profiles.find(3L)).contains(new UserProfileResponse(3L, "모아", null));
        server.verify();
    }

    @Test
    void 응답에_없는_회원은_찾을_수_없다() {
        server.expect(requestTo(BASE_URL + "/api/v1/users/profiles?ids=3"))
                .andRespond(withSuccess("{\"success\":true,\"data\":[]}", MediaType.APPLICATION_JSON));

        UserProfiles profiles = userClient.findProfiles(List.of(3L));

        assertThat(profiles.isAvailable()).isTrue();
        assertThat(profiles.find(3L)).isEmpty();
    }

    @Test
    void user_service가_실패하면_예외_대신_조회_실패로_돌려준다() {
        server.expect(requestTo(BASE_URL + "/api/v1/users/profiles?ids=3")).andRespond(withServerError());

        assertThat(userClient.findProfiles(List.of(3L)).isAvailable()).isFalse();
    }

    @Test
    void 조회할_회원이_없으면_호출하지_않는다() {
        UserProfiles profiles = userClient.findProfiles(List.of());

        assertThat(profiles.isAvailable()).isTrue();
        server.verify();
    }
}
