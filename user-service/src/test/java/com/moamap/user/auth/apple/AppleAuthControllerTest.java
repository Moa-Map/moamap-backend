package com.moamap.user.auth.apple;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moamap.user.auth.dto.AppleLoginRequest;
import com.moamap.user.auth.dto.TokenResponse;
import com.moamap.user.auth.service.AppleLoginService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AppleAuthController.class, properties = "apple.enabled=true")
class AppleAuthControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean AppleLoginService service;

    @Test
    void Apple_로그인_성공시_기존_토큰_응답을_반환한다() throws Exception {
        var request = new AppleLoginRequest("identity", "code", "a".repeat(43), "홍길동");
        given(service.login(request)).willReturn(new TokenResponse(1L, "access", "refresh", "Bearer", 1800, 1209600, true));
        mvc.perform(post("/api/v1/auth/apple/login").contentType("application/json")
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("access"))
                .andExpect(jsonPath("$.data.isNewUser").value(true));
    }

    @Test
    void 필수_인증정보가_비면_400을_반환한다() throws Exception {
        mvc.perform(post("/api/v1/auth/apple/login").contentType("application/json")
                        .content("{\"identityToken\":\"\",\"authorizationCode\":\"code\",\"nonce\":\"bad\"}"))
                .andExpect(status().isBadRequest());
    }
}
