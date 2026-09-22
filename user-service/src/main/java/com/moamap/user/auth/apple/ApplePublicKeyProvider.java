package com.moamap.user.auth.apple;

import com.moamap.common.exception.BusinessException;
import com.moamap.user.exception.UserErrorCode;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public class ApplePublicKeyProvider {
    static final String JWKS_URI = "https://appleid.apple.com/auth/keys";
    private static final Duration CACHE_TTL = Duration.ofHours(1);
    private static final Duration REFRESH_INTERVAL = Duration.ofMinutes(1);
    private final RestClient client;
    private final Clock clock;
    private Map<String, PublicKey> keys = Map.of();
    private Instant expiresAt = Instant.MIN;
    private Instant nextRefreshAt = Instant.MIN;
    private boolean lastRefreshFailed;

    public ApplePublicKeyProvider(RestClient client, Clock clock) {
        this.client = client;
        this.clock = clock;
    }

    // 갱신을 직렬화하고 실패/알 수 없는 kid에도 공통 cooldown을 적용한다.
    public synchronized PublicKey find(String kid) {
        if (kid == null || kid.isBlank() || kid.length() > 128) {
            throw new BusinessException(UserErrorCode.INVALID_APPLE_TOKEN);
        }
        Instant now = clock.instant();
        if (now.isBefore(expiresAt) && keys.containsKey(kid)) {
            return keys.get(kid);
        }
        if (!now.isBefore(nextRefreshAt)) {
            nextRefreshAt = now.plus(REFRESH_INTERVAL);
            try {
                keys = fetchKeys();
                expiresAt = now.plus(CACHE_TTL);
                lastRefreshFailed = false;
            } catch (RestClientException | GeneralSecurityException | IllegalArgumentException e) {
                lastRefreshFailed = true;
                throw new BusinessException(UserErrorCode.APPLE_AUTH_UNAVAILABLE);
            }
        }
        if (lastRefreshFailed || !now.isBefore(expiresAt)) {
            throw new BusinessException(UserErrorCode.APPLE_AUTH_UNAVAILABLE);
        }
        PublicKey key = keys.get(kid);
        if (key == null) {
            throw new BusinessException(UserErrorCode.INVALID_APPLE_TOKEN);
        }
        return key;
    }

    private Map<String, PublicKey> fetchKeys() throws GeneralSecurityException {
        JwkSet response = client.get().uri(JWKS_URI).retrieve().body(JwkSet.class);
        if (response == null || response.keys() == null || response.keys().size() > 16) {
            throw new IllegalArgumentException("Invalid Apple public key response");
        }
        Map<String, PublicKey> result = new HashMap<>();
        for (Jwk jwk : response.keys()) {
            if (jwk == null || !"RSA".equals(jwk.kty()) || !"sig".equals(jwk.use())
                    || !"RS256".equals(jwk.alg())) {
                continue;
            }
            if (jwk.kid() == null || jwk.kid().isBlank() || jwk.n() == null || jwk.e() == null
                    || jwk.n().length() > 2048 || jwk.e().length() > 16) {
                throw new IllegalArgumentException("Invalid Apple RSA key");
            }
            BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(jwk.n()));
            BigInteger exponent = new BigInteger(1, Base64.getUrlDecoder().decode(jwk.e()));
            if (modulus.bitLength() < 2048) {
                throw new IllegalArgumentException("Weak Apple RSA key");
            }
            PublicKey key = KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(modulus, exponent));
            if (result.putIfAbsent(jwk.kid(), key) != null) {
                throw new IllegalArgumentException("Duplicate Apple key ID");
            }
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("No supported Apple public keys");
        }
        return Map.copyOf(result);
    }

    private record JwkSet(List<Jwk> keys) {}
    private record Jwk(String kty, String use, String alg, String kid, String n, String e) {}
}
