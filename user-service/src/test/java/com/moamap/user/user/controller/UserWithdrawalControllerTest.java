package com.moamap.user.user.controller;

import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moamap.user.user.exception.UserNotFoundException;
import com.moamap.user.user.service.UserWithdrawalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserWithdrawalController.class)
class UserWithdrawalControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private UserWithdrawalService userWithdrawalService;

    @Test
    void 탈퇴에_성공하면_200을_반환한다() throws Exception {
        mockMvc.perform(delete("/api/v1/users/me").header("X-User-Id", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(userWithdrawalService).withdraw(1L);
    }

    @Test
    void 인증_헤더가_없으면_401이다() throws Exception {
        mockMvc.perform(delete("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 이미_탈퇴했거나_없는_회원이면_404다() throws Exception {
        willThrow(new UserNotFoundException("사용자를 찾을 수 없습니다.")).given(userWithdrawalService).withdraw(1L);

        mockMvc.perform(delete("/api/v1/users/me").header("X-User-Id", 1L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("USER_003"));
    }
}
