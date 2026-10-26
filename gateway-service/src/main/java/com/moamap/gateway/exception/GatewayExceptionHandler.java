package com.moamap.gateway.exception;

import java.util.concurrent.TimeoutException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moamap.common.exception.CommonErrorCode;
import com.moamap.common.exception.ErrorCode;
import com.moamap.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 게이트웨이 레벨에서 발생하는 에러(라우팅 실패 404, 다운스트림 장애 503, 타임아웃 등)를
 * 인증 필터와 동일한 ApiResponse 포맷으로 내려준다. Spring 기본 핸들러(order -1)보다 앞서야 하므로 -2로 둔다.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class GatewayExceptionHandler implements ErrorWebExceptionHandler {

    private static final byte[] FALLBACK_BODY = "{\"success\":false}".getBytes(java.nio.charset.StandardCharsets.UTF_8);

    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        ErrorCode errorCode = resolve(ex);
        if (errorCode == CommonErrorCode.INTERNAL_SERVER_ERROR) {
            log.error("게이트웨이에서 처리되지 않은 예외", ex);
        }

        response.setStatusCode(HttpStatus.valueOf(errorCode.getStatus()));
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = response.bufferFactory().wrap(serialize(ApiResponse.error(errorCode)));
        return response.writeWith(Mono.just(buffer));
    }

    private ErrorCode resolve(Throwable ex) {
        // 라우팅 실패(NotFoundException)·상태코드 예외는 상태값 그대로 매핑한다.
        if (ex instanceof ResponseStatusException rse) {
            return byStatus(rse.getStatusCode().value());
        }
        if (ex instanceof TimeoutException) {
            return CommonErrorCode.GATEWAY_TIMEOUT;
        }
        // 커넥션 거부 등 다운스트림 연결 실패
        if (ex instanceof java.net.ConnectException || ex.getCause() instanceof java.net.ConnectException) {
            return CommonErrorCode.SERVICE_UNAVAILABLE;
        }
        return CommonErrorCode.INTERNAL_SERVER_ERROR;
    }

    private ErrorCode byStatus(int status) {
        return switch (status) {
            case 400 -> CommonErrorCode.INVALID_INPUT_VALUE;
            case 401 -> CommonErrorCode.UNAUTHORIZED;
            case 403 -> CommonErrorCode.ACCESS_DENIED;
            case 404 -> CommonErrorCode.ENTITY_NOT_FOUND;
            case 405 -> CommonErrorCode.METHOD_NOT_ALLOWED;
            case 503 -> CommonErrorCode.SERVICE_UNAVAILABLE;
            case 504 -> CommonErrorCode.GATEWAY_TIMEOUT;
            default -> CommonErrorCode.INTERNAL_SERVER_ERROR;
        };
    }

    private byte[] serialize(ApiResponse<?> body) {
        try {
            return objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException e) {
            return FALLBACK_BODY;
        }
    }
}
