package com.moamap.user.user.service;

import com.moamap.common.exception.BusinessException;
import com.moamap.user.auth.apple.AppleCredential;
import com.moamap.user.auth.apple.AppleCredentialRepository;
import com.moamap.user.auth.apple.AppleTokenCipher;
import com.moamap.user.event.UserWithdrawnEvent;
import com.moamap.user.outbox.OutboxRecorder;
import com.moamap.user.user.entity.User;
import com.moamap.user.user.exception.UserNotFoundException;
import com.moamap.user.user.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴의 DB 부분. 익명화, Apple 토큰 회수, 탈퇴 이벤트 기록을 한 트랜잭션으로 묶는다.
 *
 * 이벤트를 같은 트랜잭션에 남기므로(outbox), 탈퇴는 됐는데 다른 서비스로 정리 신호가 안 가는 일이 없다.
 * 외부 호출(S3, Apple)은 여기서 하지 않는다. 커밋 전에 외부를 건드리면, 롤백됐을 때 되돌릴 수 없다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class UserWithdrawalWriter {

    private final UserRepository userRepository;
    private final AppleCredentialRepository appleCredentialRepository;
    private final ObjectProvider<AppleTokenCipher> appleTokenCipher;
    private final OutboxRecorder outboxRecorder;

    /** 같은 회원의 탈퇴 요청이 동시에 와도 한 번만 처리되도록 회원 행을 잠근다. 두 번째 요청은 404다. */
    @Transactional
    public WithdrawnUser withdraw(Long userId) {
        User user = userRepository.findByIdForUpdate(userId)
                .filter(found -> !found.isWithdrawn())
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));

        String appleRefreshToken = takeAppleRefreshToken(userId);
        Instant now = Instant.now();

        user.withdraw(now);
        outboxRecorder.record(String.valueOf(userId), UserWithdrawnEvent.TYPE,
                new UserWithdrawnEvent(UUID.randomUUID().toString(), userId, now));

        return new WithdrawnUser(userId, appleRefreshToken);
    }

    /**
     * 저장된 Apple 토큰을 복호화해 꺼내고 행은 지운다. 폐기 요청은 커밋 후에 한다.
     *
     * 복호화할 수 없어도(Apple 로그인이 꺼져 있거나 키가 바뀐 경우) 탈퇴는 막지 않는다. 비밀값 행은 어쨌든 지운다.
     */
    private String takeAppleRefreshToken(Long userId) {
        AppleCredential credential = appleCredentialRepository.findById(userId).orElse(null);
        if (credential == null) {
            return null;
        }
        String refreshToken = decrypt(credential);
        appleCredentialRepository.delete(credential);
        return refreshToken;
    }

    private String decrypt(AppleCredential credential) {
        AppleTokenCipher cipher = appleTokenCipher.getIfAvailable();
        if (cipher == null) {
            log.warn("Apple 로그인이 비활성화돼 탈퇴 회원의 Apple 토큰을 폐기하지 못합니다. userId={}", credential.getUserId());
            return null;
        }
        try {
            return cipher.decrypt(credential.getUserId(), credential.getClientId(), new AppleTokenCipher.EncryptedToken(
                    credential.getEncryptedRefreshToken(), credential.getKeyVersion()));
        } catch (BusinessException e) {
            log.warn("탈퇴 회원의 Apple 토큰을 복호화하지 못했습니다. userId={}", credential.getUserId());
            return null;
        }
    }
}
