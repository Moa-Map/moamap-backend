package com.moamap.user.auth.oauth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kakao")
public record KakaoProperties(
        long appId,
        String tokenInfoUri,
        String userInfoUri
) {
}
