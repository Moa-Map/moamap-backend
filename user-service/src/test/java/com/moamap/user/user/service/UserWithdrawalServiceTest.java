package com.moamap.user.user.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.moamap.common.storage.ObjectStorageCleaner;
import com.moamap.user.auth.apple.AppleTokenRevoker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.client.RestClientException;

/**
 * 커밋 이후의 외부 정리. 본인 경로만 지우는지, 실패해도 탈퇴 응답을 깨뜨리지 않는지 본다.
 */
@ExtendWith(MockitoExtension.class)
class UserWithdrawalServiceTest {

    private static final Long USER_ID = 42L;

    @Mock private UserWithdrawalWriter writer;
    @Mock private ObjectStorageCleaner cleaner;
    @Mock private AppleTokenRevoker revoker;
    @Mock private ObjectProvider<ObjectStorageCleaner> cleanerProvider;
    @Mock private ObjectProvider<AppleTokenRevoker> revokerProvider;

    private UserWithdrawalService service() {
        return new UserWithdrawalService(writer, cleanerProvider, revokerProvider);
    }

    @Test
    void 이_회원의_프로필_사진_경로를_통째로_지운다() {
        given(writer.withdraw(USER_ID)).willReturn(new WithdrawnUser(USER_ID, null));
        given(cleanerProvider.getIfAvailable()).willReturn(cleaner);
        given(revokerProvider.getIfAvailable()).willReturn(revoker);

        service().withdraw(USER_ID);

        // 경로는 서버가 회원 ID로 만든다. 끝의 "/"가 없으면 profiles/420 같은 남의 경로까지 걸린다.
        verify(cleaner).deleteAllUnder("profiles/42/");
        verify(revoker, never()).revoke(anyString());
    }

    @Test
    void Apple_토큰이_있으면_폐기를_요청한다() {
        given(writer.withdraw(USER_ID)).willReturn(new WithdrawnUser(USER_ID, "apple-refresh"));
        given(cleanerProvider.getIfAvailable()).willReturn(cleaner);
        given(revokerProvider.getIfAvailable()).willReturn(revoker);

        service().withdraw(USER_ID);

        verify(revoker).revoke("apple-refresh");
    }

    @Test
    void 외부_정리가_실패해도_탈퇴는_성공으로_끝난다() {
        given(writer.withdraw(USER_ID)).willReturn(new WithdrawnUser(USER_ID, "apple-refresh"));
        given(cleanerProvider.getIfAvailable()).willReturn(cleaner);
        given(revokerProvider.getIfAvailable()).willReturn(revoker);
        willThrow(new IllegalStateException("s3 down")).given(cleaner).deleteAllUnder(anyString());
        willThrow(new RestClientException("apple down")).given(revoker).revoke(anyString());

        assertThatCode(() -> service().withdraw(USER_ID)).doesNotThrowAnyException();
        // 사진 삭제가 실패해도 Apple 폐기는 시도한다.
        verify(revoker).revoke("apple-refresh");
    }

    @Test
    void 스토리지나_Apple_설정이_없는_환경에서도_탈퇴된다() {
        given(writer.withdraw(USER_ID)).willReturn(new WithdrawnUser(USER_ID, "apple-refresh"));
        given(cleanerProvider.getIfAvailable()).willReturn(null);
        given(revokerProvider.getIfAvailable()).willReturn(null);

        assertThatCode(() -> service().withdraw(USER_ID)).doesNotThrowAnyException();
    }
}
