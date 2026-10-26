package com.moamap.user.auth.oauth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

// 카카오 토큰정보 응답
public record KakaoTokenInfo(
        @JsonProperty("id") long id,
        @JsonProperty("app_id") long appId
) {
}
