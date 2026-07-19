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

/**
 * 게이트웨이 인증 문지기.
 * 1) 클라이언트가 보낸 X-User-Id는 항상 제거해 사칭을 막는다.
 * 2) Authorization 헤더의 JWT를 검증해 사용자 ID를 X-User-Id로 주입한다.
 * 3) 공개 경로가 아닌데 유효한 토큰이 없으면 401을 반환한다.
 * (권한 판단은 각 서비스가 담당하고, 게이트웨이는 신원 확인까지만 한다.)
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String BEARER_PREFIX = "Bearer ";

    private static final List<PublicEndpoint> PUBLIC_ENDPOINTS = List.of(
        PublicEndpoint.of(null, "/api/v1/auth/**"),      // 로그인/토큰 재발급/로그아웃
        PublicEndpoint.of(HttpMethod.GET, "/maps/**"),   // 커뮤니티 지도 열람
        PublicEndpoint.of(HttpMethod.GET, "/map/**")     // 공식(공공데이터) 지도 열람
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
                headers.remove(USER_ID_HEADER);
                userId.ifPresent(id -> headers.set(USER_ID_HEADER, String.valueOf(id)));
            })
            .build();
        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
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

        static PublicEndpoint of(HttpMethod method, String pattern) {
            return new PublicEndpoint(method, PARSER.parse(pattern));
        }

        boolean matches(HttpMethod requestMethod, PathContainer path) {
            return (method == null || method.equals(requestMethod)) && pattern.matches(path);
        }
    }
}
