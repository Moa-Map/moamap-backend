package com.moamap.user.auth.oauth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 카카오 인가코드 교환(POST /oauth/token) 응답. 필요한 access_token만 매핑한다.
 */
public record KakaoOAuthToken(
        @JsonProperty("access_token") String accessToken
) {
}
