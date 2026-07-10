package com.moamap.user.auth.oauth;

public record OAuthUserInfo(
        String provider,
        String providerId,
        String nickname,
        String email,
        String profileImageUrl
) {
}
