package com.moamap.user.user.service;

import com.moamap.common.storage.ObjectStorageCleaner;
import com.moamap.user.auth.apple.AppleTokenRevocationProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * 회원 탈퇴. DB 처리(UserWithdrawalWriter)가 커밋된 뒤에 외부 정리를 한다.
 *
 * 외부 정리가 실패해도 탈퇴는 되돌리지 않는다. 사용자는 이미 탈퇴를 요청했고 개인정보도 DB에서 지워졌다.
 * 실패는 로그로 남겨 운영에서 확인한다. Apple 토큰 폐기는 실패하면 배치가 다시 시도한다. 로그에는 회원 ID만 남기고 토큰이나 사진 주소는 남기지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserWithdrawalService {

    private static final String PROFILE_KEY_PREFIX = "profiles/";

    private final UserWithdrawalWriter withdrawalWriter;
    private final ObjectProvider<ObjectStorageCleaner> storageCleaner;
    private final ObjectProvider<AppleTokenRevocationProcessor> appleTokenRevocation;

    public void withdraw(Long userId) {
        WithdrawnUser withdrawn = withdrawalWriter.withdraw(userId);
        deleteProfilePhotos(userId);
        revokeAppleToken(withdrawn);
    }

    /**
     * 사진 버킷은 공개 읽기라, DB에서 주소만 지우면 파일은 주소를 아는 누구나 계속 볼 수 있다. 파일까지 지운다.
     * 지금 사진 하나가 아니라 이 회원 경로 아래 전부를 지운다 — 교체 전 사진, 업로드만 하고 저장하지 않은 사진도 남아 있다.
     * 경로는 서버가 회원 ID로 만든다. 클라이언트가 보낸 URL을 쓰지 않으므로 남의 파일을 지울 수 없다.
     */
    private void deleteProfilePhotos(Long userId) {
        ObjectStorageCleaner cleaner = storageCleaner.getIfAvailable();
        if (cleaner == null) {
            return;
        }
        try {
            cleaner.deleteAllUnder(PROFILE_KEY_PREFIX + userId + "/");
        } catch (RuntimeException e) {
            log.warn("탈퇴 회원의 프로필 사진을 지우지 못했습니다. userId={}, cause={}", userId, e.getClass().getSimpleName());
        }
    }

    /**
     * 바로 한 번 시도한다. 실패해도 폐기 대기 행이 남아 AppleTokenRevocationProcessor 배치가 다시 시도하므로 여기선 기록만 한다.
     * Apple 로그인이 꺼진 환경이면 행만 남고, 켜진 뒤 배치가 처리한다.
     */
    private void revokeAppleToken(WithdrawnUser withdrawn) {
        AppleTokenRevocationProcessor revocation = appleTokenRevocation.getIfAvailable();
        if (revocation == null || !withdrawn.appleRevokePending()) {
            return;
        }
        try {
            revocation.revokeNow(withdrawn.userId());
        } catch (RuntimeException e) {
            log.warn("탈퇴 회원의 Apple 토큰을 바로 폐기하지 못해 재시도에 맡깁니다. userId={}, cause={}",
                    withdrawn.userId(), e.getClass().getSimpleName());
        }
    }
}
