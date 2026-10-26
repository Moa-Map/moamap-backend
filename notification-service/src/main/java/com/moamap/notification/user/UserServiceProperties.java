package com.moamap.notification.user;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 알림 목록에 행동한 사람의 닉네임·프로필 이미지를 붙이기 위한 user-service 내부 호출 주소.
 */
@ConfigurationProperties(prefix = "services.user")
public record UserServiceProperties(String baseUrl) {
}
