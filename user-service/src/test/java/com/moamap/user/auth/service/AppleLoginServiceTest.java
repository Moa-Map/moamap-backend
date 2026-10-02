package com.moamap.user.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.moamap.user.auth.apple.AppleCredentialStore;
import com.moamap.user.auth.apple.AppleIdentity;
import com.moamap.user.auth.apple.AppleIdentityTokenVerifier;
import com.moamap.user.auth.apple.AppleTokenExchanger;
import com.moamap.user.auth.dto.AppleLoginRequest;
import com.moamap.user.auth.dto.TokenResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AppleLoginServiceTest {
    @Mock AppleIdentityTokenVerifier verifier;
    @Mock AppleTokenExchanger exchanger;
    @Mock AppleCredentialStore credentials;
    @Mock AuthService authService;

    @Test
    void 최초_이름을_공통_로그인에_전달하고_토큰을_반환한다() {
        var service = new AppleLoginService(verifier, exchanger, credentials, authService);
        String nonce = "a".repeat(43);
        var request = new AppleLoginRequest("id-token", "auth-code", nonce, "  홍길동  ");
        var initial = new AppleIdentity("sub", "first@example.com", nonce);
        var exchanged = new AppleTokenExchanger.Result(new AppleIdentity("sub", "verified@example.com", nonce), "apple-refresh");
        given(verifier.verifyAndConsume("id-token", nonce)).willReturn(initial);
        given(exchanger.exchange("auth-code", initial)).willReturn(exchanged);
        given(authService.loginApple(any(), any(), any())).willReturn(new TokenResponse(1L, "a", "r", "Bearer", 1, 2, true));

        TokenResponse result = service.login(request);

        var info = ArgumentCaptor.forClass(com.moamap.user.auth.oauth.OAuthUserInfo.class);
        verify(authService).loginApple(info.capture(), any(), org.mockito.ArgumentMatchers.eq("apple-refresh"));
        assertThat(info.getValue().provider()).isEqualTo("apple");
        assertThat(info.getValue().providerId()).isEqualTo("sub");
        assertThat(info.getValue().nickname()).isEqualTo("홍길동");
        assertThat(info.getValue().email()).isEqualTo("verified@example.com");
        assertThat(result.isNewUser()).isTrue();
    }

    @Test
    void 이름이_없으면_기본_닉네임을_전달한다() {
        var service = new AppleLoginService(verifier, exchanger, credentials, authService);
        String nonce = "a".repeat(43);
        var request = new AppleLoginRequest("id-token", "auth-code", nonce, null);
        var identity = new AppleIdentity("sub", null, nonce);
        given(verifier.verifyAndConsume("id-token", nonce)).willReturn(identity);
        given(exchanger.exchange("auth-code", identity)).willReturn(new AppleTokenExchanger.Result(identity, "refresh"));
        given(authService.loginApple(any(), any(), any())).willReturn(new TokenResponse(1L, "a", "r", "Bearer", 1, 2, false));

        service.login(request);

        var info = ArgumentCaptor.forClass(com.moamap.user.auth.oauth.OAuthUserInfo.class);
        verify(authService).loginApple(info.capture(), any(), org.mockito.ArgumentMatchers.eq("refresh"));
        assertThat(info.getValue().nickname()).isEqualTo("모아맵사용자");
    }
}
