package com.moamap.gateway.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class JwtAuthenticationFilterTest {

    private static final String SECRET = "test-only-jwt-secret-not-for-production-32bytes";
    private static final String USER_ID_HEADER = "X-User-Id";

    private final JwtValidator validator = new JwtValidator(new JwtProperties(SECRET));
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(validator, new ObjectMapper());

    @Test
    void 유효한_토큰이면_X_User_Id를_주입한다() {
        CapturingChain chain = new CapturingChain();
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post("/api/v1/places").header(HttpHeaders.AUTHORIZATION, bearer(7L)));

        filter.filter(exchange, chain).block();

        assertThat(chain.userId()).isEqualTo("7");
    }

    @Test
    void 보호_경로에_토큰이_없으면_401을_반환한다() {
        CapturingChain chain = new CapturingChain();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/places"));

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(chain.invoked()).isFalse();
    }

    @Test
    void 보호_경로에_위조된_토큰이면_401을_반환한다() {
        CapturingChain chain = new CapturingChain();
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post("/api/v1/places").header(HttpHeaders.AUTHORIZATION, "Bearer tampered.token.value"));

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(chain.invoked()).isFalse();
    }

    @Test
    void 클라이언트가_보낸_X_User_Id는_토큰_값으로_덮어쓴다() {
        CapturingChain chain = new CapturingChain();
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post("/api/v1/places")
                .header(USER_ID_HEADER, "999")
                .header(HttpHeaders.AUTHORIZATION, bearer(7L)));

        filter.filter(exchange, chain).block();

        assertThat(chain.userId()).isEqualTo("7");
    }

    @Test
    void 공개_경로는_토큰이_없어도_통과하며_X_User_Id를_주입하지_않는다() {
        CapturingChain chain = new CapturingChain();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/maps"));

        filter.filter(exchange, chain).block();

        assertThat(chain.invoked()).isTrue();
        assertThat(chain.userId()).isNull();
    }

    @Test
    void 공개_경로에서_클라이언트가_보낸_X_User_Id는_제거된다() {
        CapturingChain chain = new CapturingChain();
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/maps").header(USER_ID_HEADER, "999"));

        filter.filter(exchange, chain).block();

        assertThat(chain.invoked()).isTrue();
        assertThat(chain.userId()).isNull();
    }

    @Test
    void 인증_경로는_토큰_없이도_공개다() {
        CapturingChain chain = new CapturingChain();
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post("/api/v1/auth/kakao/login"));

        filter.filter(exchange, chain).block();

        assertThat(chain.invoked()).isTrue();
    }

    private static String bearer(long userId) {
        String token = Jwts.builder().subject(String.valueOf(userId))
            .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
            .compact();
        return "Bearer " + token;
    }

    private static final class CapturingChain implements GatewayFilterChain {

        private ServerWebExchange captured;

        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            this.captured = exchange;
            return Mono.empty();
        }

        boolean invoked() {
            return captured != null;
        }

        String userId() {
            return captured == null ? null : captured.getRequest().getHeaders().getFirst(USER_ID_HEADER);
        }
    }
}
