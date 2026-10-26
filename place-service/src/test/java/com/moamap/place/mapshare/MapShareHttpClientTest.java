package com.moamap.place.mapshare;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import com.moamap.common.exception.BusinessException;
import com.moamap.place.exception.PlaceErrorCode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MapShareHttpClientTest {

    private HttpServer server;
    private String baseUrl;
    private final List<String> receivedUserAgents = new ArrayList<>();
    private final List<String> receivedReferers = new ArrayList<>();

    private final MapShareHttpClient client = new MapShareHttpClient();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        baseUrl = "http://localhost:" + server.getAddress().getPort();

        // /short -> /final 로 302 리다이렉트
        server.createContext("/short", exchange -> {
            exchange.getResponseHeaders().add("Location", baseUrl + "/final?id=abc123");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/final", exchange -> respond(exchange, 200, "{\"ok\":true}"));
        server.createContext("/notfound", exchange -> respond(exchange, 404, "no such list"));

        server.start();
    }

    private void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
        receivedUserAgents.add(exchange.getRequestHeaders().getFirst("User-Agent"));
        receivedReferers.add(exchange.getRequestHeaders().getFirst("Referer"));
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void resolveFinalUrl은_리다이렉트를_따라간_최종_URL을_반환한다() {
        String finalUrl = client.resolveFinalUrl(baseUrl + "/short");

        assertThat(finalUrl).isEqualTo(baseUrl + "/final?id=abc123");
    }

    @Test
    void get은_모바일_User_Agent를_보낸다() {
        client.get(baseUrl + "/final", null);

        assertThat(receivedUserAgents).last().asString().contains("Mobile");
    }

    @Test
    void get은_referer가_주어지면_Referer_헤더를_붙인다() {
        client.get(baseUrl + "/final", "https://map.kakao.com/");

        assertThat(receivedReferers).last().isEqualTo("https://map.kakao.com/");
    }

    @Test
    void get은_referer가_null이면_Referer_헤더를_붙이지_않는다() {
        client.get(baseUrl + "/final", null);

        assertThat(receivedReferers).last().isNull();
    }

    @Test
    void getBodyOrThrow는_200이_아니면_추출_실패_예외를_던진다() {
        assertThatThrownBy(() -> client.getBodyOrThrow(baseUrl + "/notfound", null, "테스트 API"))
            .isInstanceOf(BusinessException.class)
            .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                .isEqualTo(PlaceErrorCode.SHARE_LINK_EXTRACTION_FAILED));
    }

    @Test
    void getBodyOrThrow는_200이면_본문을_반환한다() {
        assertThat(client.getBodyOrThrow(baseUrl + "/final", null, "테스트 API"))
            .isEqualTo("{\"ok\":true}");
    }

    @Test
    void get은_연결할_수_없으면_추출_실패_예외를_던진다() {
        assertThatThrownBy(() -> client.get("http://localhost:1/nothing", null))
            .isInstanceOf(BusinessException.class);
    }
}
