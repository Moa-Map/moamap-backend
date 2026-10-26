package com.moamap.gateway.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

// JWT 검증 시크릿. user-service 발급 값이랑 같아야 함.
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret) {
}
