package com.moamap.gateway.auth;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moamap.common.exception.CommonErrorCode;
import com.moamap.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

// 토큰 검증해서 X-User-Id 붙여주는 관문. 권한 체크는 각 서비스가 알아서 함.
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String BEARER_PREFIX = "Bearer ";

    // 토큰 없이 열어줄 경로. 나머지는 다 토큰 필요.
    private static final List<PublicEndpoint> PUBLIC_ENDPOINTS = List.of(
        PublicEndpoint.of(null, "/api/v1/auth/**"),
        PublicEndpoint.of(HttpMethod.GET, "/api/v1/maps/**"),
        // 통합 Swagger UI — 테스트 편의를 위해 공개.
        PublicEndpoint.of(HttpMethod.GET, "/swagger-ui.html"),
        PublicEndpoint.of(HttpMethod.GET, "/swagger-ui/**"),
        PublicEndpoint.of(HttpMethod.GET, "/webjars/**"),
        PublicEndpoint.of(HttpMethod.GET, "/user-service/v3/api-docs/**"),
        PublicEndpoint.of(HttpMethod.GET, "/map-service/v3/api-docs/**"),
        PublicEndpoint.of(HttpMethod.GET, "/place-service/v3/api-docs/**")
    );

    private final JwtValidator jwtValidator;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        Optional<Long> userId = resolveToken(request).flatMap(jwtValidator::extractUserId);

        if (userId.isEmpty() && !isPublic(request)) {
            return unauthorized(exchange);
        }

        ServerHttpRequest mutatedRequest = request.mutate()
            .headers(headers -> {
                // 클라가 보낸 X-User-Id는 무조건 버리고 검증된 것만 넣는다 (사칭 방지)
                headers.remove(USER_ID_HEADER);
                userId.ifPresent(id -> headers.set(USER_ID_HEADER, String.valueOf(id)));
            })
            .build();
        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE; // 라우팅보다 먼저 타야 함
    }

    private Optional<String> resolveToken(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return Optional.of(header.substring(BEARER_PREFIX.length()));
        }
        return Optional.empty();
    }

    private boolean isPublic(ServerHttpRequest request) {
        PathContainer path = request.getPath().pathWithinApplication();
        HttpMethod method = request.getMethod();
        return PUBLIC_ENDPOINTS.stream().anyMatch(endpoint -> endpoint.matches(method, path));
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = response.bufferFactory().wrap(serialize(ApiResponse.error(CommonErrorCode.UNAUTHORIZED)));
        return response.writeWith(Mono.just(buffer));
    }

    private byte[] serialize(ApiResponse<?> body) {
        try {
            return objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException e) {
            return "{\"success\":false}".getBytes(StandardCharsets.UTF_8);
        }
    }

    private record PublicEndpoint(HttpMethod method, PathPattern pattern) {

        private static final PathPatternParser PARSER = new PathPatternParser();

        // method가 null이면 메서드 상관없이 매칭
        static PublicEndpoint of(HttpMethod method, String pattern) {
            return new PublicEndpoint(method, PARSER.parse(pattern));
        }

        boolean matches(HttpMethod requestMethod, PathContainer path) {
            return (method == null || method.equals(requestMethod)) && pattern.matches(path);
        }
    }
}
