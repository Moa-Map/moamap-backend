package com.moamap.user.user.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.moamap.common.storage.ObjectStorageCleaner;
import com.moamap.user.auth.apple.AppleTokenRevocationProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 커밋 이후의 외부 정리. 본인 경로만 지우는지, 실패해도 탈퇴 응답을 깨뜨리지 않는지 본다.
 */
@ExtendWith(MockitoExtension.class)
class UserWithdrawalServiceTest {

    private static final Long USER_ID = 42L;

    @Mock private UserWithdrawalWriter writer;
    @Mock private ObjectStorageCleaner cleaner;
    @Mock private AppleTokenRevocationProcessor revocation;
    @Mock private ObjectProvider<ObjectStorageCleaner> cleanerProvider;
    @Mock private ObjectProvider<AppleTokenRevocationProcessor> revocationProvider;

    private UserWithdrawalService service() {
        return new UserWithdrawalService(writer, cleanerProvider, revocationProvider);
    }

    @Test
    void 이_회원의_프로필_사진_경로를_통째로_지운다() {
        given(writer.withdraw(USER_ID)).willReturn(new WithdrawnUser(USER_ID, false));
        given(cleanerProvider.getIfAvailable()).willReturn(cleaner);
        given(revocationProvider.getIfAvailable()).willReturn(revocation);

        service().withdraw(USER_ID);

        // 경로는 서버가 회원 ID로 만든다. 끝의 "/"가 없으면 profiles/420 같은 남의 경로까지 걸린다.
        verify(cleaner).deleteAllUnder("profiles/42/");
        verify(revocation, never()).revokeNow(any());
    }

    @Test
    void Apple_토큰이_폐기_대기면_바로_폐기를_시도한다() {
        given(writer.withdraw(USER_ID)).willReturn(new WithdrawnUser(USER_ID, true));
        given(cleanerProvider.getIfAvailable()).willReturn(cleaner);
        given(revocationProvider.getIfAvailable()).willReturn(revocation);

        service().withdraw(USER_ID);

        verify(revocation).revokeNow(USER_ID);
    }

    @Test
    void 외부_정리가_실패해도_탈퇴는_성공으로_끝난다() {
        given(writer.withdraw(USER_ID)).willReturn(new WithdrawnUser(USER_ID, true));
        given(cleanerProvider.getIfAvailable()).willReturn(cleaner);
        given(revocationProvider.getIfAvailable()).willReturn(revocation);
        willThrow(new IllegalStateException("s3 down")).given(cleaner).deleteAllUnder(anyString());
        willThrow(new IllegalStateException("db down")).given(revocation).revokeNow(USER_ID);

        assertThatCode(() -> service().withdraw(USER_ID)).doesNotThrowAnyException();
        // 사진 삭제가 실패해도 Apple 폐기는 시도한다.
        verify(revocation).revokeNow(USER_ID);
    }

    @Test
    void 스토리지나_Apple_설정이_없는_환경에서도_탈퇴된다() {
        given(writer.withdraw(USER_ID)).willReturn(new WithdrawnUser(USER_ID, true));
        given(cleanerProvider.getIfAvailable()).willReturn(null);
        given(revocationProvider.getIfAvailable()).willReturn(null);

        assertThatCode(() -> service().withdraw(USER_ID)).doesNotThrowAnyException();
    }
}
