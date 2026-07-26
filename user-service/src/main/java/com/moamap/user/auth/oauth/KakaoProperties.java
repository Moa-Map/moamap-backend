package com.moamap.user.auth.oauth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kakao")
public record KakaoProperties(
        long appId,
        String tokenInfoUri,
        String userInfoUri,
        // 아래는 개발용 카카오 콜백(dev-login)에서만 쓰는 인가코드 교환 설정.
        String restApiKey,
        String redirectUri,
        String tokenUri
) {
}
