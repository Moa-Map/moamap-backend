package com.moamap.map.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class UserBlockClientTest {

    private static final String BASE_URL = "http://user-service";

    @Test
    void 차단한_사람_ID를_읽고_실패하면_빈_목록이다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        UserBlockClient client = new UserBlockClient(builder, new UserServiceProperties(BASE_URL));
        server.expect(requestTo(BASE_URL + "/internal/users/1/blocked-ids"))
            .andRespond(withSuccess("{\"success\":true,\"data\":[5,6]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/internal/users/1/blocked-ids")).andRespond(withServerError());

        assertThat(client.findBlockedIds(1L)).containsExactly(5L, 6L);
        assertThat(client.findBlockedIds(1L)).isEmpty();
    }

    @Test
    void 로그인하지_않았으면_조회하지_않는다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        UserBlockClient client = new UserBlockClient(builder, new UserServiceProperties(BASE_URL));

        assertThat(client.findBlockedIds(null)).isEmpty();
        server.verify();
    }
}
