package com.moamap.user.auth.google;

import com.moamap.common.exception.BusinessException;
import com.moamap.user.exception.UserErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import java.time.Clock;
import java.util.Date;
import java.util.List;
import java.util.Set;

public class GoogleIdTokenVerifier {
    // 허용 iss가 2개라 jjwt requireIssuer(1개만 가능)를 쓰지 않고 파싱 뒤 직접 비교한다.
    private static final Set<String> ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");
    private final List<String> clientIds;
    private final JwtParser parser;
    private final Clock clock;

    public GoogleIdTokenVerifier(GoogleProperties properties, GooglePublicKeyProvider keys, Clock clock) {
        this.clientIds = properties.clientIds();
        this.clock = clock;
        this.parser = Jwts.parser()
                .clock(() -> Date.from(clock.instant()))
                .keyLocator(header -> {
                    if (!(header instanceof JwsHeader jws) || !"RS256".equals(jws.getAlgorithm())
                            || header.containsKey("zip")) {
                        throw new BusinessException(UserErrorCode.INVALID_GOOGLE_TOKEN);
                    }
                    return keys.find(jws.getKeyId());
                }).build();
    }

    public Claims verify(String token) {
        if (token == null || token.isBlank() || token.length() > 16384) {
            throw new BusinessException(UserErrorCode.INVALID_GOOGLE_TOKEN);
        }
        try {
            Claims claims = parser.parseSignedClaims(token).getPayload();
            Set<String> aud = claims.getAudience();
            if (claims.getExpiration() == null || !claims.getExpiration().toInstant().isAfter(clock.instant())
                    || claims.getIssuer() == null || !ISSUERS.contains(claims.getIssuer())
                    || aud == null || aud.size() != 1 || !clientIds.contains(aud.iterator().next())
                    || claims.getSubject() == null || claims.getSubject().isBlank()
                    || claims.getSubject().length() > 255) {
                throw new BusinessException(UserErrorCode.INVALID_GOOGLE_TOKEN);
            }
            return claims;
        } catch (JwtException | IllegalArgumentException e) {
            // 라이브러리 예외 메시지에는 토큰 claim이 포함될 수 있어 노출·로깅하지 않는다.
            throw new BusinessException(UserErrorCode.INVALID_GOOGLE_TOKEN);
        }
    }
}
