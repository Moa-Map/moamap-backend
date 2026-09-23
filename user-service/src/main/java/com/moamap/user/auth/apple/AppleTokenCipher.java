package com.moamap.user.auth.apple;

import com.moamap.common.exception.BusinessException;
import com.moamap.user.exception.UserErrorCode;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Apple 토큰은 폐기 요청에 원문이 필요하므로, 해시 대신 인증된 암호화로 저장한다. */
public class AppleTokenCipher {
    private final String activeVersion;
    private final Map<String, SecretKey> keys;
    private final SecureRandom random = new SecureRandom();

    public AppleTokenCipher(AppleCredentialProperties properties) {
        activeVersion = properties.activeKeyVersion();
        Map<String, SecretKey> loaded = new HashMap<>();
        try {
            if (properties.keys() == null || activeVersion == null) {
                throw new IllegalArgumentException();
            }
            for (var entry : properties.keys().entrySet()) {
                if (entry.getKey() == null || !entry.getKey().matches("[A-Za-z0-9_-]{1,30}") || entry.getValue() == null) {
                    throw new IllegalArgumentException();
                }
                byte[] bytes = Base64.getDecoder().decode(entry.getValue());
                if (bytes.length != 32) {
                    throw new IllegalArgumentException();
                }
                loaded.put(entry.getKey(), new SecretKeySpec(bytes, "AES"));
            }
            if (!loaded.containsKey(activeVersion)) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Apple credential keys must contain an active version and Base64-encoded 32-byte keys");
        }
        keys = Map.copyOf(loaded);
    }

    public EncryptedToken encrypt(Long userId, String clientId, String token) {
        if (token == null || token.isBlank() || token.length() > 16384) {
            throw new BusinessException(UserErrorCode.APPLE_AUTH_UNAVAILABLE);
        }
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keys.get(activeVersion), new GCMParameterSpec(128, iv));
            cipher.updateAAD(aad(userId, clientId, activeVersion));
            byte[] encrypted = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
            byte[] packed = ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
            return new EncryptedToken(Base64.getEncoder().encodeToString(packed), activeVersion);
        } catch (GeneralSecurityException e) {
            throw new BusinessException(UserErrorCode.APPLE_AUTH_UNAVAILABLE);
        }
    }

    public String decrypt(Long userId, String clientId, EncryptedToken token) {
        try {
            if (token == null || token.ciphertext() == null || token.keyVersion() == null || token.ciphertext().length() > 100000
                    || !keys.containsKey(token.keyVersion())) {
                throw new IllegalArgumentException();
            }
            byte[] packed = Base64.getDecoder().decode(token.ciphertext());
            if (packed.length <= 28) {
                throw new IllegalArgumentException();
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keys.get(token.keyVersion()), new GCMParameterSpec(128, packed, 0, 12));
            cipher.updateAAD(aad(userId, clientId, token.keyVersion()));
            return new String(cipher.doFinal(packed, 12, packed.length - 12), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new BusinessException(UserErrorCode.APPLE_AUTH_UNAVAILABLE);
        }
    }

    private byte[] aad(Long userId, String clientId, String version) {
        if (userId == null || userId <= 0 || clientId == null || clientId.isBlank()) {
            throw new BusinessException(UserErrorCode.APPLE_AUTH_UNAVAILABLE);
        }
        return (userId + ":" + clientId + ":" + version).getBytes(StandardCharsets.UTF_8);
    }

    public record EncryptedToken(String ciphertext, String keyVersion) {}
}
