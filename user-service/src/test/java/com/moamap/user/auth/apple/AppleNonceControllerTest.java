package com.moamap.user.auth.apple;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moamap.common.exception.BusinessException;
import com.moamap.user.exception.UserErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AppleNonceController.class, properties = "apple.enabled=true")
class AppleNonceControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private AppleNonceService nonces;

    @Test
    void nonce와_초단위_만료시간을_반환한다() throws Exception {
        given(nonces.issue()).willReturn(new AppleNonceResponse("a".repeat(43), 300));
        mvc.perform(post("/api/v1/auth/apple/nonce"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.nonce").value("a".repeat(43)))
                .andExpect(jsonPath("$.data.expiresIn").value(300));
    }

    @Test
    void 저장소_장애는_503으로_반환한다() throws Exception {
        given(nonces.issue()).willThrow(new BusinessException(UserErrorCode.APPLE_AUTH_UNAVAILABLE));
        mvc.perform(post("/api/v1/auth/apple/nonce"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("USER_009"));
    }
}
