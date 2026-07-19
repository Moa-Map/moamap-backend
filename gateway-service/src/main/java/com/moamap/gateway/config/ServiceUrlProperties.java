package com.moamap.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 라우팅 대상 서비스 주소. 로컬은 기본값, 배포 환경에서는 환경변수(서비스 DNS)로 주입한다.
 */
@ConfigurationProperties(prefix = "services")
public record ServiceUrlProperties(String userUrl, String mapUrl, String placeUrl) {
}
