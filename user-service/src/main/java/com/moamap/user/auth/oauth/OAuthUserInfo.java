package com.moamap.user.auth.oauth;

// 제공자 무관 공통 사용자 정보
public record OAuthUserInfo(
        String provider,
        String providerId,
        String nickname,
        String email,
        String profileImageUrl
) {
}
