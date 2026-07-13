package com.moamap.user.auth.oauth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

// 카카오 사용자 정보 응답
public record KakaoUserResponse(
        long id,
        @JsonProperty("kakao_account") KakaoAccount kakaoAccount
) {
    public record KakaoAccount(String email, Profile profile) {
        public record Profile(
                String nickname,
                @JsonProperty("profile_image_url") String profileImageUrl
        ) {
        }
    }
}
