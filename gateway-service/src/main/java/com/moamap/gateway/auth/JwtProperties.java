package com.moamap.gateway.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 검증용 설정. secret은 토큰을 발급하는 user-service와 동일한 값이어야 한다.
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret) {
}
