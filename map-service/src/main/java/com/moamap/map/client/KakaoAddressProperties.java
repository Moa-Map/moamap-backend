package com.moamap.map.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kakao.local")
public record KakaoAddressProperties(String restApiKey, String baseUrl) {
}
