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

@Configuration
@RequiredArgsConstructor
public class GatewayConfig {

    private final ServiceUrlProperties serviceUrls;

    // prefix 보고 서비스로 넘김. 경로는 그대로 전달(재작성 안 함).
    @Bean
    public RouteLocator routeLocator(RouteLocatorBuilder builder) {
        return builder.routes()
            .route("user-service", r -> r.path("/api/v1/auth/**", "/api/v1/users/**").uri(serviceUrls.userUrl()))
            .route("map-service", r -> r.path("/api/v1/maps/**").uri(serviceUrls.mapUrl()))
            .route("place-service", r -> r.path("/api/v1/places/**").uri(serviceUrls.placeUrl()))
            .build();
    }

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        // 배포 땐 실제 프론트 도메인으로 좁히기
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("*"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }
}
