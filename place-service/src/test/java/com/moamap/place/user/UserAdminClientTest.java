package com.moamap.place.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.moamap.common.exception.BusinessException;
import com.moamap.common.exception.CommonErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * user-service 내부 API의 실제 응답 모양(ApiResponse로 감싼 JSON)을 읽는지 확인한다.
 * 다른 테스트는 이 클라이언트를 가짜로 바꾸므로, 응답 모양이 어긋나면 여기서만 드러난다.
 */
class UserAdminClientTest {

    private static final String BASE_URL = "http://user-service";

    private MockRestServiceServer server;
    private UserAdminClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new UserAdminClient(builder, new UserServiceProperties(BASE_URL));
    }

    @Test
    void 운영자_여부를_읽는다() {
        server.expect(requestTo(BASE_URL + "/internal/users/7/admin"))
            .andRespond(withSuccess("{\"success\":true,\"data\":{\"admin\":true}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/internal/users/8/admin"))
            .andRespond(withSuccess("{\"success\":true,\"data\":{\"admin\":false}}", MediaType.APPLICATION_JSON));

        assertThat(client.isAdmin(7L)).isTrue();
        assertThat(client.isAdmin(8L)).isFalse();
        server.verify();
    }

    @Test
    void 운영자_확인에_실패하면_막는다() {
        server.expect(requestTo(BASE_URL + "/internal/users/7/admin")).andRespond(withServerError());

        assertThatThrownBy(() -> client.isAdmin(7L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", CommonErrorCode.SERVICE_UNAVAILABLE);
    }

    @Test
    void 운영자_목록을_읽고_실패하면_빈_목록이다() {
        server.expect(requestTo(BASE_URL + "/internal/users/admins"))
            .andRespond(withSuccess("{\"success\":true,\"data\":[100,101]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/internal/users/admins")).andRespond(withServerError());

        assertThat(client.findAdminIds()).containsExactly(100L, 101L);
        assertThat(client.findAdminIds()).isEmpty();
    }
}
