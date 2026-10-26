package com.moamap.map.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 서울 열린데이터광장 API 설정. key는 절대 커밋하지 않고 환경변수로 주입한다.
 */
@ConfigurationProperties(prefix = "seoul.api")
public record SeoulOpenApiProperties(String baseUrl, String key, String service) {
}
