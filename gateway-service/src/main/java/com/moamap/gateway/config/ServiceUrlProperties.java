package com.moamap.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// 라우팅 대상 주소. 로컬은 기본값, 배포 땐 env(서비스 DNS)로.
@ConfigurationProperties(prefix = "services")
public record ServiceUrlProperties(String userUrl, String mapUrl, String placeUrl) {
}
