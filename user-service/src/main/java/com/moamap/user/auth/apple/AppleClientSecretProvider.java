package com.moamap.user.auth.apple;

import io.jsonwebtoken.Jwts;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

public class AppleClientSecretProvider {
    private final String clientId;
    private final String teamId;
    private final String keyId;
    private final ECPrivateKey key;
    private final Clock clock;

    public AppleClientSecretProvider(AppleProperties apple, AppleTokenProperties properties, Clock clock) {
        this.clientId = apple.clientId();
        this.teamId = properties.teamId();
        this.keyId = properties.keyId();
        this.key = parseKey(properties.privateKey());
        this.clock = clock;
    }

    public String create() {
        Instant now = clock.instant();
        return Jwts.builder().header().keyId(keyId).and()
                .issuer(teamId).subject(clientId).audience().add("https://appleid.apple.com").and()
                .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(300)))
                .signWith(key, Jwts.SIG.ES256).compact();
    }

    private ECPrivateKey parseKey(String pem) {
        try {
            if (pem == null || !pem.contains("-----BEGIN PRIVATE KEY-----")
                    || !pem.contains("-----END PRIVATE KEY-----")) {
                throw new IllegalArgumentException();
            }
            String base64 = pem.replace("\\n", "\n").replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "").replaceAll("\\s", "");
            ECPrivateKey parsed = (ECPrivateKey) KeyFactory.getInstance("EC")
                    .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
            AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
            parameters.init(new ECGenParameterSpec("secp256r1"));
            ECParameterSpec expected = parameters.getParameterSpec(ECParameterSpec.class);
            ECParameterSpec actual = parsed.getParams();
            if (!expected.getCurve().equals(actual.getCurve()) || !expected.getGenerator().equals(actual.getGenerator())
                    || !expected.getOrder().equals(actual.getOrder()) || expected.getCofactor() != actual.getCofactor()) {
                throw new IllegalArgumentException();
            }
            return parsed;
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // 설정값이나 원본 예외에 담긴 개인키를 출력하지 않는다.
            throw new IllegalStateException("Apple signing key must be a PKCS#8 P-256 private key");
        }
    }
}
