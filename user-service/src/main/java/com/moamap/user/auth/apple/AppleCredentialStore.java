package com.moamap.user.auth.apple;

import com.moamap.common.exception.BusinessException;
import com.moamap.user.exception.UserErrorCode;
import com.moamap.user.user.entity.User;
import com.moamap.user.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
public class AppleCredentialStore {
    private final UserRepository users;
    private final AppleCredentialRepository credentials;
    private final AppleTokenCipher cipher;
    private final AppleProperties properties;

    /** 회원 저장과 같은 트랜잭션에서만 실행한다. 같은 회원의 동시 갱신도 직렬화한다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void save(Long userId, String refreshToken) {
        User user = users.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.INVALID_APPLE_TOKEN));
        if (!"apple".equals(user.getProvider()) || user.getDeletedAt() != null) {
            throw new BusinessException(UserErrorCode.INVALID_APPLE_TOKEN);
        }
        AppleCredential existing = credentials.findById(userId).orElse(null);
        if (existing != null && !properties.clientId().equals(existing.getClientId())) {
            throw new BusinessException(UserErrorCode.APPLE_AUTH_UNAVAILABLE);
        }
        if (refreshToken == null || refreshToken.isBlank()) {
            if (existing == null) {
                throw new BusinessException(UserErrorCode.APPLE_AUTH_UNAVAILABLE);
            }
            // 읽을 수 없는 기존 암호문을 보존한 채 로그인 성공으로 처리하지 않는다.
            cipher.decrypt(userId, existing.getClientId(), new AppleTokenCipher.EncryptedToken(
                    existing.getEncryptedRefreshToken(), existing.getKeyVersion()));
            return;
        }
        var encrypted = cipher.encrypt(userId, properties.clientId(), refreshToken);
        if (existing == null) {
            credentials.saveAndFlush(new AppleCredential(user, properties.clientId(), encrypted));
        } else {
            existing.replace(encrypted);
            credentials.flush();
        }
    }
}
