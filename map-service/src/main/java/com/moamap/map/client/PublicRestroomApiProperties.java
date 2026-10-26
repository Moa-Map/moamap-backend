package com.moamap.map.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 공공화장실 API(data.go.kr) 설정. serviceKey는 절대 커밋하지 않고 환경변수로 주입한다.
 */
@ConfigurationProperties(prefix = "restroom.api")
public record PublicRestroomApiProperties(String baseUrl, String path, String serviceKey) {
}
