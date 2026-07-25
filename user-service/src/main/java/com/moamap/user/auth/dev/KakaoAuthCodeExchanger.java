package com.moamap.user.auth.dev;

import com.moamap.user.auth.exception.InvalidOAuthTokenException;
import com.moamap.user.auth.oauth.KakaoProperties;
import com.moamap.user.auth.oauth.dto.KakaoOAuthToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 개발용: 카카오 authorize 리다이렉트로 받은 인가코드(code)를 카카오 액세스 토큰으로 교환한다.
 *
 * dev-login.enabled=true 일 때만 빈으로 등록된다(운영에서는 존재하지 않음).
 */
@Component
@ConditionalOnProperty(prefix = "dev-login", name = "enabled", havingValue = "true")
public class KakaoAuthCodeExchanger {

    private static final Logger log = LoggerFactory.getLogger(KakaoAuthCodeExchanger.class);

    private final RestClient restClient;
    private final KakaoProperties properties;

    public KakaoAuthCodeExchanger(RestClient.Builder builder, KakaoProperties properties) {
        this.restClient = builder.build();
        this.properties = properties;
    }

    /** code → 카카오 access_token. 교환 실패/빈 응답이면 InvalidOAuthTokenException. */
    public String exchange(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", properties.restApiKey());
        form.add("redirect_uri", properties.redirectUri());
        form.add("code", code);

        KakaoOAuthToken token;
        try {
            token = restClient.post()
                    .uri(properties.tokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(KakaoOAuthToken.class);
        } catch (RestClientException e) {
            log.warn("카카오 인가코드 교환 실패: {}", e.getMessage(), e);
            throw new InvalidOAuthTokenException("카카오 인가코드 교환에 실패했습니다.");
        }
        if (token == null || token.accessToken() == null || token.accessToken().isBlank()) {
            throw new InvalidOAuthTokenException("카카오 인가코드 교환 응답에 access_token이 없습니다.");
        }
        return token.accessToken();
    }
}
