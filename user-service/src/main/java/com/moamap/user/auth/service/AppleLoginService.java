package com.moamap.user.auth.service;

import com.moamap.user.auth.apple.AppleCredentialStore;
import com.moamap.user.auth.apple.AppleIdentity;
import com.moamap.user.auth.apple.AppleIdentityTokenVerifier;
import com.moamap.user.auth.apple.AppleTokenExchanger;
import com.moamap.user.auth.dto.AppleLoginRequest;
import com.moamap.user.auth.dto.TokenResponse;
import com.moamap.user.auth.oauth.OAuthUserInfo;
import lombok.RequiredArgsConstructor;
@RequiredArgsConstructor
public class AppleLoginService {
    private final AppleIdentityTokenVerifier verifier;
    private final AppleTokenExchanger exchanger;
    private final AppleCredentialStore credentials;
    private final AuthService authService;

    public TokenResponse login(AppleLoginRequest request) {
        AppleIdentity initial = verifier.verifyAndConsume(request.identityToken(), request.nonce());
        AppleTokenExchanger.Result exchanged = exchanger.exchange(request.authorizationCode(), initial);
        AppleIdentity identity = exchanged.identity();
        String nickname = nickname(request.fullName());
        OAuthUserInfo info = new OAuthUserInfo("apple", identity.subject(), nickname, identity.email(), null);
        return authService.loginApple(info, credentials, exchanged.refreshToken());
    }

    private String nickname(String fullName) {
        if (fullName == null) {
            return "모아맵사용자";
        }
        String normalized = fullName.trim().replaceAll("[\\p{Cntrl}]", "");
        if (normalized.isBlank()) {
            return "모아맵사용자";
        }
        return normalized.length() <= 30 ? normalized : normalized.substring(0, 30);
    }
}
