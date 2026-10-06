package com.moamap.user.auth.apple;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴 회원의 Apple 토큰 폐기를 끝까지 책임진다.
 *
 * 탈퇴 트랜잭션은 자격증명 행을 지우지 않고 폐기 대기로 표시만 한다. 커밋 직후 한 번 바로 시도하고(revokeNow),
 * 실패하면 행이 남아 있으므로 주기 배치(retryDue)가 간격을 늘려가며 다시 시도한다. 성공해야 행을 지운다.
 *
 * Apple은 이미 무효인 토큰도 200으로 답하므로, 실패는 모두 다시 시도할 만한 것(장애, 설정 오류, 복호화 실패)으로 본다.
 * 횟수를 다 쓰면 더 시도하지 않고 error 로그를 남긴다. 원인을 고친 뒤 revoke_attempts를 0으로 되돌리면 다시 집힌다.
 */
@Slf4j
public class AppleTokenRevocationProcessor {

    static final int MAX_ATTEMPTS = 12;
    private static final int BATCH_SIZE = 20;
    private static final Duration INITIAL_BACKOFF = Duration.ofMinutes(5);
    private static final Duration MAX_BACKOFF = Duration.ofHours(6);

    private final AppleCredentialRepository credentials;
    private final AppleTokenCipher cipher;
    private final AppleTokenRevoker revoker;
    private final Clock clock;

    public AppleTokenRevocationProcessor(AppleCredentialRepository credentials, AppleTokenCipher cipher,
                                         AppleTokenRevoker revoker, Clock clock) {
        this.credentials = credentials;
        this.cipher = cipher;
        this.revoker = revoker;
        this.clock = clock;
    }

    /** 탈퇴 커밋 직후 호출한다. 폐기 대기 행이 없거나 배치가 잡고 있으면 아무것도 하지 않는다. */
    @Transactional
    public void revokeNow(Long userId) {
        credentials.findPendingRevocation(userId).ifPresent(this::attempt);
    }

    /**
     * 기동 직후에는 바로 돌지 않는다. 막 뜬 서버가 바쁠 때 외부(Apple)를 부르지 않고, 테스트에서는 이 지연을 길게 둬
     * 백그라운드 실행이 테스트와 같은 행을 동시에 집지 않게 한다.
     */
    @Scheduled(initialDelayString = "${apple.revocation.initial-delay-ms:60000}",
            fixedDelayString = "${apple.revocation.poll-interval-ms:300000}")
    @Transactional
    public void retryDue() {
        List<AppleCredential> due = credentials.findDueRevocations(clock.instant(), MAX_ATTEMPTS, Limit.of(BATCH_SIZE));
        due.forEach(this::attempt);
    }

    private void attempt(AppleCredential credential) {
        try {
            String refreshToken = cipher.decrypt(credential.getUserId(), credential.getClientId(),
                    new AppleTokenCipher.EncryptedToken(credential.getEncryptedRefreshToken(), credential.getKeyVersion()));
            revoker.revoke(refreshToken);
            credentials.delete(credential);
        } catch (RuntimeException e) {
            recordFailure(credential, e);
        }
    }

    private void recordFailure(AppleCredential credential, RuntimeException e) {
        Instant now = clock.instant();
        credential.recordRevokeFailure(now.plus(backoff(credential.getRevokeAttempts() + 1)));
        // 예외 메시지에 요청 본문(토큰)이 섞일 수 있어 예외 객체는 남기지 않는다.
        if (credential.getRevokeAttempts() >= MAX_ATTEMPTS) {
            log.error("탈퇴 회원의 Apple 토큰 폐기를 {}회 실패해 재시도를 멈춥니다. userId={}, cause={}",
                    MAX_ATTEMPTS, credential.getUserId(), e.getClass().getSimpleName());
        } else {
            log.warn("탈퇴 회원의 Apple 토큰 폐기에 실패해 나중에 다시 시도합니다. userId={}, attempts={}, cause={}",
                    credential.getUserId(), credential.getRevokeAttempts(), e.getClass().getSimpleName());
        }
    }

    /** 5분에서 시작해 두 배씩, 최대 6시간. 12회면 대략 하루 반(35시간) 동안 시도한다. 배치 주기(5분)보다 짧으면 의미가 없다. */
    private Duration backoff(int failedAttempts) {
        Duration delay = INITIAL_BACKOFF.multipliedBy(1L << Math.min(failedAttempts - 1, 20));
        return delay.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : delay;
    }
}
