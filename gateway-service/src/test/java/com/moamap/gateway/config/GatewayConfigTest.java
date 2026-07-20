package com.moamap.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

// 라우트 설정 회귀 테스트. 마이페이지(/api/v1/users/**) 라우트가 빠져서 404 나던 걸 여기서 잡아낸다.
@SpringBootTest
class GatewayConfigTest {

    @Autowired
    private RouteLocator routeLocator;

    @Test
    void user_service_라우트는_인증경로와_마이페이지경로를_모두_처리한다() {
        Route userRoute = findRoute("user-service");

        assertThat(matches(userRoute, "/api/v1/auth/kakao/login")).isTrue();
        assertThat(matches(userRoute, "/api/v1/users/me")).isTrue();
    }

    @Test
    void map_service_라우트는_maps와_map_경로를_처리한다() {
        Route mapRoute = findRoute("map-service");

        assertThat(matches(mapRoute, "/maps")).isTrue();
        assertThat(matches(mapRoute, "/map/official/foot-traffic")).isTrue();
    }

    @Test
    void place_service_라우트는_places_경로를_처리한다() {
        Route placeRoute = findRoute("place-service");

        assertThat(matches(placeRoute, "/places")).isTrue();
    }

    private Route findRoute(String id) {
        return routeLocator.getRoutes()
            .filter(route -> route.getId().equals(id))
            .blockFirst();
    }

    private boolean matches(Route route, String path) {
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(path));
        return Boolean.TRUE.equals(Mono.from(route.getPredicate().apply(exchange)).block());
    }
}
