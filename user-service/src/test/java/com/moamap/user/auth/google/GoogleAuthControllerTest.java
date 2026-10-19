package com.moamap.user.auth.google;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moamap.common.exception.BusinessException;
import com.moamap.user.auth.dto.TokenResponse;
import com.moamap.user.auth.service.GoogleLoginService;
import com.moamap.user.exception.UserErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// 라우팅·바인딩·상태 코드·직렬화만 본다. 토큰 검증과 매핑 규칙은 GoogleIdTokenVerifierTest, GoogleLoginServiceTest가 본다.
@WebMvcTest(controllers = GoogleAuthController.class, properties = "google.enabled=true")
class GoogleAuthControllerTest {
    private static final String URL = "/api/v1/auth/google/login";
    @Autowired MockMvc mvc;
    @MockitoBean GoogleLoginService service;

    @Test
    void 로그인_성공시_토큰_응답을_반환한다() throws Exception {
        given(service.login("id-token")).willReturn(new TokenResponse(1L, "access", "refresh", "Bearer", 1800, 1209600, true));
        mvc.perform(post(URL).contentType("application/json").content("{\"idToken\":\"id-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access"))
                .andExpect(jsonPath("$.data.isNewUser").value(true));
    }

    @Test
    void idToken이_공백이면_400_COMMON_001() throws Exception {
        mvc.perform(post(URL).contentType("application/json").content("{\"idToken\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_001"));
    }

    @Test
    void idToken이_누락되면_400_COMMON_001() throws Exception {
        mvc.perform(post(URL).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_001"));
    }

    @Test
    void idToken이_16384자를_넘으면_400_COMMON_001() throws Exception {
        mvc.perform(post(URL).contentType("application/json").content("{\"idToken\":\"" + "a".repeat(16385) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_001"));
    }

    @Test
    void 서비스가_INVALID_GOOGLE_TOKEN이면_401_USER_018() throws Exception {
        given(service.login("bad")).willThrow(new BusinessException(UserErrorCode.INVALID_GOOGLE_TOKEN));
        mvc.perform(post(URL).contentType("application/json").content("{\"idToken\":\"bad\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("USER_018"));
    }

    @Test
    void 서비스가_GOOGLE_AUTH_UNAVAILABLE이면_503_USER_019() throws Exception {
        given(service.login("t")).willThrow(new BusinessException(UserErrorCode.GOOGLE_AUTH_UNAVAILABLE));
        mvc.perform(post(URL).contentType("application/json").content("{\"idToken\":\"t\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("USER_019"));
    }
}
