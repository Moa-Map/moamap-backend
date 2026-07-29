package com.moamap.map.user;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 멤버 목록에 닉네임·프로필 이미지를 채우기 위한 user-service 내부 호출 주소.
 */
@ConfigurationProperties(prefix = "services.user")
public record UserServiceProperties(String baseUrl) {
}
