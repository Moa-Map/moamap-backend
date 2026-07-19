package com.moamap.gateway.config;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * 각 서비스의 실제 경로를 그대로 라우팅한다(경로 재작성 없음).
 */
@Configuration
@RequiredArgsConstructor
public class GatewayConfig {

    private final ServiceUrlProperties serviceUrls;

    @Bean
    public RouteLocator routeLocator(RouteLocatorBuilder builder) {
        return builder.routes()
            .route("user-service", r -> r.path("/api/v1/auth/**").uri(serviceUrls.userUrl()))
            .route("map-service", r -> r.path("/maps/**", "/map/**").uri(serviceUrls.mapUrl()))
            .route("place-service", r -> r.path("/places/**").uri(serviceUrls.placeUrl()))
            .build();
    }

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        // 배포 시에는 "*" 대신 실제 프론트 도메인으로 제한한다.
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("*"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }
}
