package com.moamap.user.auth.service;

import com.moamap.user.auth.dto.TokenResponse;
import com.moamap.user.auth.google.GoogleIdTokenVerifier;
import com.moamap.user.auth.oauth.OAuthUserInfo;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;

// 외부 HTTP(JWKS)가 DB 커넥션을 붙잡지 않도록 @Transactional을 붙이지 않는다. 트랜잭션은 AuthService.login이 연다.
@RequiredArgsConstructor
public class GoogleLoginService {
    private final GoogleIdTokenVerifier verifier;
    private final AuthService authService;

    public TokenResponse login(String idToken) {
        Claims claims = verifier.verify(idToken);
        OAuthUserInfo info = new OAuthUserInfo("google", claims.getSubject(),
                nickname(claims.get("name", String.class)), email(claims), claims.get("picture", String.class));
        return authService.login(info);
    }

    // Apple과 달리 UTF-16이 아닌 코드포인트 기준으로 잘라 이모지 서로게이트 쌍이 깨지지 않게 한다.
    private String nickname(String name) {
        String normalized = name == null ? "" : name.replaceAll("[\\p{Cntrl}]", "").trim();
        if (normalized.isEmpty()) {
            return "모아맵사용자";
        }
        return normalized.codePointCount(0, normalized.length()) <= 30
                ? normalized : normalized.substring(0, normalized.offsetByCodePoints(0, 30));
    }

    private String email(Claims claims) {
        String email = claims.get("email", String.class);
        Object verified = claims.get("email_verified");
        boolean isVerified = Boolean.TRUE.equals(verified) || "true".equals(verified);
        return isVerified && email != null && email.length() <= 255 ? email : null;
    }
}
