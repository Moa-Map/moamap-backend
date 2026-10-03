package com.moamap.user.user.service;

import com.moamap.common.storage.ObjectStorageCleaner;
import com.moamap.user.auth.apple.AppleTokenRevoker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * 회원 탈퇴. DB 처리(UserWithdrawalWriter)가 커밋된 뒤에 외부 정리를 한다.
 *
 * 외부 정리가 실패해도 탈퇴는 되돌리지 않는다. 사용자는 이미 탈퇴를 요청했고 개인정보도 DB에서 지워졌다.
 * 실패는 로그로 남겨 운영에서 확인한다. 로그에는 회원 ID만 남기고 토큰이나 사진 주소는 남기지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserWithdrawalService {

    private static final String PROFILE_KEY_PREFIX = "profiles/";

    private final UserWithdrawalWriter withdrawalWriter;
    private final ObjectProvider<ObjectStorageCleaner> storageCleaner;
    private final ObjectProvider<AppleTokenRevoker> appleTokenRevoker;

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

    private void revokeAppleToken(WithdrawnUser withdrawn) {
        AppleTokenRevoker revoker = appleTokenRevoker.getIfAvailable();
        if (revoker == null || withdrawn.appleRefreshToken() == null) {
            return;
        }
        try {
            revoker.revoke(withdrawn.appleRefreshToken());
        } catch (RuntimeException e) {
            // 예외 메시지에 요청 본문(토큰)이 섞일 수 있어 예외 객체는 남기지 않는다.
            log.warn("탈퇴 회원의 Apple 토큰 폐기 요청이 실패했습니다. userId={}, cause={}",
                    withdrawn.userId(), e.getClass().getSimpleName());
        }
    }
}
