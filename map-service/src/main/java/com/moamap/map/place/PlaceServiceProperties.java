package com.moamap.map.place;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 멤버 목록에 등록 장소 수를 채우기 위한 place-service 내부 호출 주소.
 */
@ConfigurationProperties(prefix = "services.place")
public record PlaceServiceProperties(String baseUrl) {
}
