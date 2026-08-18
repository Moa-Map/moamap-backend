package com.moamap.user.auth.dev;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moamap.user.auth.dto.TokenResponse;
import com.moamap.user.auth.exception.InvalidOAuthTokenException;
import com.moamap.user.auth.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = DevKakaoCallbackController.class, properties = "dev-login.enabled=true")
class DevKakaoCallbackControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private KakaoAuthCodeExchanger exchanger;

    @MockitoBean
    private AuthService authService;

    @Test
    void 콜백은_code를_교환해_로그인하고_JWT를_반환한다() throws Exception {
        given(exchanger.exchange("auth-code")).willReturn("kakao-access-token");
        given(authService.login("kakao-access-token"))
                .willReturn(new TokenResponse(42L, "access", "refresh", "Bearer", 1800, 1209600, true));

        mockMvc.perform(get("/api/v1/auth/test/kakao/callback").param("code", "auth-code"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access"))
                .andExpect(jsonPath("$.data.isNewUser").value(true));
    }

    @Test
    void 유효하지_않은_code면_401_USER_001을_반환한다() throws Exception {
        given(exchanger.exchange("bad-code"))
                .willThrow(new InvalidOAuthTokenException("카카오 인가코드 교환에 실패했습니다."));

        mockMvc.perform(get("/api/v1/auth/test/kakao/callback").param("code", "bad-code"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("USER_001"));
    }
}
