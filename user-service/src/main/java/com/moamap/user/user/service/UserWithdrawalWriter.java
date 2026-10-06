package com.moamap.user.user.service;

import com.moamap.user.auth.apple.AppleCredentialRepository;
import com.moamap.user.event.UserWithdrawnEvent;
import com.moamap.user.outbox.OutboxRecorder;
import com.moamap.user.user.entity.User;
import com.moamap.user.user.exception.UserNotFoundException;
import com.moamap.user.user.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴의 DB 부분. 익명화, Apple 토큰 폐기 대기 표시, 탈퇴 이벤트 기록을 한 트랜잭션으로 묶는다.
 *
 * 이벤트를 같은 트랜잭션에 남기므로(outbox), 탈퇴는 됐는데 다른 서비스로 정리 신호가 안 가는 일이 없다.
 * Apple 토큰도 같은 이유로 행을 지우지 않고 폐기 대기로 표시한다. 폐기가 실패해도 행이 남아 다시 시도할 수 있다.
 * 외부 호출(S3, Apple)은 여기서 하지 않는다. 커밋 전에 외부를 건드리면, 롤백됐을 때 되돌릴 수 없다.
 */
@Component
@RequiredArgsConstructor
class UserWithdrawalWriter {

    private final UserRepository userRepository;
    private final AppleCredentialRepository appleCredentialRepository;
    private final OutboxRecorder outboxRecorder;

    /** 같은 회원의 탈퇴 요청이 동시에 와도 한 번만 처리되도록 회원 행을 잠근다. 두 번째 요청은 404다. */
    @Transactional
    public WithdrawnUser withdraw(Long userId) {
        User user = userRepository.findByIdForUpdate(userId)
                .filter(found -> !found.isWithdrawn())
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));

        Instant now = Instant.now();
        boolean appleRevokePending = appleCredentialRepository.findById(userId)
                .map(credential -> {
                    credential.requestRevoke(now);
                    return true;
                })
                .orElse(false);

        user.withdraw(now);
        outboxRecorder.record(String.valueOf(userId), UserWithdrawnEvent.TYPE,
                new UserWithdrawnEvent(UUID.randomUUID().toString(), userId, now));

        return new WithdrawnUser(userId, appleRevokePending);
    }
}
