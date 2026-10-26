package com.moamap.place.kakao;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kakao.local")
public record KakaoLocalProperties(String restApiKey, String baseUrl) {
}
