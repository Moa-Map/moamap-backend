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
    void user_service_라우트는_공지_조회와_관리_경로를_처리한다() {
        Route userRoute = findRoute("user-service");

        assertThat(matches(userRoute, "/api/v1/notices")).isTrue();
        assertThat(matches(userRoute, "/api/v1/notices/1")).isTrue();
        assertThat(matches(userRoute, "/api/v1/admin/notices/1")).isTrue();
    }

    @Test
    void user_service_라우트는_문의_등록과_관리_경로를_처리한다() {
        Route userRoute = findRoute("user-service");

        assertThat(matches(userRoute, "/api/v1/inquiries")).isTrue();
        assertThat(matches(userRoute, "/api/v1/admin/inquiries/1/status")).isTrue();
    }

    @Test
    void user_service_라우트는_배너_조회와_관리_경로를_처리한다() {
        Route userRoute = findRoute("user-service");

        assertThat(matches(userRoute, "/api/v1/banners")).isTrue();
        assertThat(matches(userRoute, "/api/v1/admin/banners/order")).isTrue();
    }

    @Test
    void user_service_라우트는_약관_조회와_동의_경로를_처리한다() {
        Route userRoute = findRoute("user-service");

        assertThat(matches(userRoute, "/api/v1/terms")).isTrue();
        assertThat(matches(userRoute, "/api/v1/terms/4")).isTrue();
        assertThat(matches(userRoute, "/api/v1/terms/agreements/me")).isTrue();
    }

    @Test
    void 운영자_신고_목록은_신고를_받은_서비스로_간다() {
        assertThat(matches(findRoute("user-service"), "/api/v1/admin/reports/users")).isTrue();
        assertThat(matches(findRoute("map-service"), "/api/v1/admin/reports/maps")).isTrue();
        assertThat(matches(findRoute("place-service"), "/api/v1/admin/reports/places")).isTrue();
        // 다른 서비스의 운영자 목록 경로는 받지 않는다.
        assertThat(matches(findRoute("user-service"), "/api/v1/admin/reports/places")).isFalse();
        assertThat(matches(findRoute("place-service"), "/api/v1/admin/reports/maps")).isFalse();
    }

    @Test
    void 내부_API_경로는_어느_서비스로도_연결하지_않는다() {
        for (String service : new String[] {"user-service", "map-service", "place-service", "notification-service"}) {
            assertThat(matches(findRoute(service), "/internal/users/1/admin")).as(service).isFalse();
            assertThat(matches(findRoute(service), "/internal/maps/1/managers")).as(service).isFalse();
        }
    }

    @Test
    void map_service_라우트는_maps_경로를_처리한다() {
        Route mapRoute = findRoute("map-service");

        assertThat(matches(mapRoute, "/api/v1/maps")).isTrue();
        assertThat(matches(mapRoute, "/api/v1/maps/official/foot-traffic")).isTrue();
    }

    @Test
    void place_service_라우트는_places_경로를_처리한다() {
        Route placeRoute = findRoute("place-service");

        assertThat(matches(placeRoute, "/api/v1/places")).isTrue();
    }

    @Test
    void notification_service_라우트는_notifications_경로를_처리한다() {
        Route notificationRoute = findRoute("notification-service");

        assertThat(matches(notificationRoute, "/api/v1/notifications")).isTrue();
        assertThat(matches(notificationRoute, "/api/v1/notifications/1/read")).isTrue();
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
