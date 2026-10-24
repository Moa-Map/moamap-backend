package com.moamap.user.block;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moamap.common.exception.BusinessException;
import com.moamap.user.block.controller.UserBlockController;
import com.moamap.user.block.dto.BlockedUserResponse;
import com.moamap.user.block.service.UserBlockService;
import com.moamap.user.exception.UserErrorCode;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserBlockController.class)
class UserBlockControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private UserBlockService userBlockService;

    @Test
    void 차단과_해제() throws Exception {
        mockMvc.perform(post("/api/v1/users/2/blocks").header("X-User-Id", 1L)).andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/users/2/blocks").header("X-User-Id", 1L)).andExpect(status().isOk());

        verify(userBlockService).block(1L, 2L);
        verify(userBlockService).unblock(1L, 2L);
    }

    @Test
    void 자기_자신을_차단하면_400이다() throws Exception {
        willThrow(new BusinessException(UserErrorCode.CANNOT_BLOCK_SELF)).given(userBlockService).block(1L, 1L);

        mockMvc.perform(post("/api/v1/users/1/blocks").header("X-User-Id", 1L))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("USER_026"));
    }

    @Test
    void 내_차단_목록을_준다() throws Exception {
        given(userBlockService.getBlockedUsers(1L))
                .willReturn(List.of(new BlockedUserResponse(2L, "차단한 사람", null, Instant.parse("2026-10-24T00:00:00Z"))));

        mockMvc.perform(get("/api/v1/users/me/blocks").header("X-User-Id", 1L))
                .andExpect(jsonPath("$.data[0].userId").value(2))
                .andExpect(jsonPath("$.data[0].nickname").value("차단한 사람"));
    }

    @Test
    void 로그인하지_않으면_401이다() throws Exception {
        mockMvc.perform(post("/api/v1/users/2/blocks")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/users/me/blocks")).andExpect(status().isUnauthorized());
    }
}
