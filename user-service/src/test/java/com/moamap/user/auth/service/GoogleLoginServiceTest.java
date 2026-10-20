package com.moamap.user.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.moamap.common.exception.BusinessException;
import com.moamap.user.auth.dto.TokenResponse;
import com.moamap.user.auth.google.GoogleIdTokenVerifier;
import com.moamap.user.auth.oauth.OAuthUserInfo;
import com.moamap.user.exception.UserErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoogleLoginServiceTest {
    private static final TokenResponse TOKENS = new TokenResponse(1L, "a", "r", "Bearer", 1, 2, true);
    @Mock GoogleIdTokenVerifier verifier;
    @Mock AuthService authService;
    GoogleLoginService service;

    @BeforeEach
    void setUp() {
        service = new GoogleLoginService(verifier, authService);
    }

    // 규칙 14
    @Test
    void 검증된_claims로_google_provider_로그인을_호출하고_토큰을_반환한다() {
        var info = new OAuthUserInfo("google", "sub-1", "홍길동", "u@example.com", "https://img/p.png");
        stub(c -> c.add("name", "홍길동").add("email", "u@example.com").add("email_verified", true)
                .add("picture", "https://img/p.png"));
        given(authService.login(info)).willReturn(TOKENS);

        assertThat(service.login("t")).isEqualTo(TOKENS);
        verify(authService).login(info);
    }

    @Test
    void name이_없거나_공백이면_기본_닉네임을_쓴다() {
        stub(c -> c.add("name", "   "));
        service.login("t");
        assertThat(captured().nickname()).isEqualTo("모아맵사용자");

        stub(c -> {});
        service.login("t");
        assertThat(captured().nickname()).isEqualTo("모아맵사용자");
    }

    @Test
    void 제어문자는_제거하고_trim한다() {
        stub(c -> c.add("name", "  홍\u0000길\n동\t "));
        service.login("t");
        assertThat(captured().nickname()).isEqualTo("홍길동");
    }

    @Test
    void 제어문자만_있는_name은_기본_닉네임을_쓴다() {
        stub(c -> c.add("name", "\u0000\n\t"));
        service.login("t");
        assertThat(captured().nickname()).isEqualTo("모아맵사용자");
    }

    @Test
    void 이모지_name이_코드포인트_30자를_넘으면_서로게이트가_깨지지_않게_앞_30자만_남긴다() {
        String name = "😀".repeat(31);
        stub(c -> c.add("name", name));
        service.login("t");
        String nickname = captured().nickname();
        assertThat(nickname).isEqualTo("😀".repeat(30));
        assertThat(nickname.codePointCount(0, nickname.length())).isEqualTo(30);
    }

    @Test
    void 코드포인트_30자_이하는_그대로_둔다() {
        String name = "😀".repeat(30);
        stub(c -> c.add("name", name));
        service.login("t");
        assertThat(captured().nickname()).isEqualTo(name);
    }

    @Test
    void email_verified가_Boolean_true이면_email을_쓴다() {
        stub(c -> c.add("email", "u@example.com").add("email_verified", true));
        service.login("t");
        assertThat(captured().email()).isEqualTo("u@example.com");
    }

    @Test
    void email_verified가_문자열_true이면_email을_쓴다() {
        stub(c -> c.add("email", "u@example.com").add("email_verified", "true"));
        service.login("t");
        assertThat(captured().email()).isEqualTo("u@example.com");
    }

    @Test
    void email_verified가_false이거나_없으면_email은_null이다() {
        stub(c -> c.add("email", "u@example.com").add("email_verified", false));
        service.login("t");
        assertThat(captured().email()).isNull();

        stub(c -> c.add("email", "u@example.com").add("email_verified", "false"));
        service.login("t");
        assertThat(captured().email()).isNull();

        stub(c -> c.add("email", "u@example.com"));
        service.login("t");
        assertThat(captured().email()).isNull();
    }

    @Test
    void email이_255자면_쓰고_256자면_null이다() {
        String ok = "a".repeat(243) + "@example.com"; // 255
        stub(c -> c.add("email", ok).add("email_verified", true));
        service.login("t");
        assertThat(captured().email()).isEqualTo(ok);

        String tooLong = "a" + ok; // 256
        stub(c -> c.add("email", tooLong).add("email_verified", true));
        service.login("t");
        assertThat(captured().email()).isNull();
    }

    @Test
    void picture가_없으면_null이다() {
        stub(c -> {});
        service.login("t");
        assertThat(captured().profileImageUrl()).isNull();
    }

    // 불변 조건: 검증 실패 시 회원/Outbox/토큰이 생기지 않는다
    @Test
    void 검증이_실패하면_AuthService를_호출하지_않고_예외를_그대로_던진다() {
        given(verifier.verify("bad")).willThrow(new BusinessException(UserErrorCode.INVALID_GOOGLE_TOKEN));
        assertThatThrownBy(() -> service.login("bad")).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(UserErrorCode.INVALID_GOOGLE_TOKEN));
        verifyNoInteractions(authService);
    }

    @Test
    void 구글_장애도_AuthService를_호출하지_않고_그대로_던진다() {
        given(verifier.verify("t")).willThrow(new BusinessException(UserErrorCode.GOOGLE_AUTH_UNAVAILABLE));
        assertThatThrownBy(() -> service.login("t")).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(UserErrorCode.GOOGLE_AUTH_UNAVAILABLE));
        verifyNoInteractions(authService);
    }

    private void stub(Consumer<io.jsonwebtoken.ClaimsBuilder> customize) {
        var builder = Jwts.claims().subject("sub-1");
        customize.accept(builder);
        Claims claims = builder.build();
        given(verifier.verify("t")).willReturn(claims);
        given(authService.login(any())).willReturn(TOKENS);
    }

    private OAuthUserInfo captured() {
        var captor = org.mockito.ArgumentCaptor.forClass(OAuthUserInfo.class);
        org.mockito.Mockito.verify(authService, org.mockito.Mockito.atLeastOnce()).login(captor.capture());
        return captor.getValue();
    }
}
