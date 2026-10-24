package com.moamap.place.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * map-service 내부 API(방장·관리자 목록)의 실제 응답 모양을 읽는지 확인한다.
 */
class MapClientManagersTest {

    private static final String BASE_URL = "http://map-service";

    @Test
    void 방장_관리자_목록을_읽고_실패하면_빈_목록이다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MapClient client = new MapClient(builder, new MapServiceProperties(BASE_URL));
        server.expect(requestTo(BASE_URL + "/internal/maps/5/managers"))
            .andRespond(withSuccess("{\"success\":true,\"data\":[1,2]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/internal/maps/5/managers")).andRespond(withServerError());

        assertThat(client.findManagerIds(5L)).containsExactly(1L, 2L);
        assertThat(client.findManagerIds(5L)).isEmpty();
    }
}
