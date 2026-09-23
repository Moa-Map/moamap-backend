package com.moamap.user.auth.apple;

import com.moamap.common.exception.BusinessException;
import com.moamap.user.exception.UserErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import java.time.Clock;
import java.util.Date;

public class AppleIdentityTokenVerifier {
    private final JwtParser parser;
    private final AppleNonceService nonces;
    private final Clock clock;

    public AppleIdentityTokenVerifier(AppleProperties properties, ApplePublicKeyProvider keys,
                                      AppleNonceService nonces, Clock clock) {
        this.nonces = nonces;
        this.clock = clock;
        this.parser = Jwts.parser()
                .requireIssuer("https://appleid.apple.com")
                .requireAudience(properties.clientId())
                .clock(() -> Date.from(clock.instant()))
                .keyLocator(header -> {
                    if (!(header instanceof JwsHeader jws) || !"RS256".equals(jws.getAlgorithm())
                            || header.containsKey("zip")) {
                        throw new BusinessException(UserErrorCode.INVALID_APPLE_TOKEN);
                    }
                    return keys.find(jws.getKeyId());
                }).build();
    }

    public AppleIdentity verifyAndConsume(String token, String expectedNonce) {
        AppleIdentity identity = verify(token, expectedNonce);
        nonces.consume(expectedNonce);
        return identity;
    }

    /** 코드 교환 응답의 ID Token을 재검증할 때는 이미 소비한 nonce를 다시 소비하지 않는다. */
    public AppleIdentity verify(String token, String expectedNonce) {
        if (token == null || token.isBlank() || token.length() > 16384
                || !AppleNonceService.isValidFormat(expectedNonce)) {
            throw new BusinessException(UserErrorCode.INVALID_APPLE_TOKEN);
        }
        try {
            Claims claims = parser.parseSignedClaims(token).getPayload();
            if (claims.getExpiration() == null || !claims.getExpiration().toInstant().isAfter(clock.instant())
                    || claims.getSubject() == null || claims.getSubject().isBlank()
                    || claims.getSubject().length() > 255
                    || !expectedNonce.equals(claims.get("nonce", String.class))) {
                throw new BusinessException(UserErrorCode.INVALID_APPLE_TOKEN);
            }
            return new AppleIdentity(claims.getSubject(), claims.get("email", String.class), expectedNonce);
        } catch (JwtException | IllegalArgumentException e) {
            // 라이브러리 예외 메시지에는 토큰 claim이 포함될 수 있어 노출·로깅하지 않는다.
            throw new BusinessException(UserErrorCode.INVALID_APPLE_TOKEN);
        }
    }
}
