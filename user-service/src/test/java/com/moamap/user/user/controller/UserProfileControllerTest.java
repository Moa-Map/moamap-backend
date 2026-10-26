package com.moamap.user.user.controller;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import com.moamap.common.exception.BusinessException;
import com.moamap.user.exception.UserErrorCode;
import com.moamap.user.user.dto.UserProfileResponse;
import com.moamap.user.user.service.UserProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 06_architect_blueprint_nickname.md 6장 15단계.
 * 라우팅/파라미터 바인딩/상태 코드만 검증한다. 표 G의 검증·매핑 로직 자체는
 * UserProfileServiceTest에서 이미 검증했으므로 재검증하지 않는다.
 */
@WebMvcTest(UserProfileController.class)
class UserProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserProfileService userProfileService;

    @Test
    void ids로_조회하면_200과_프로필_목록을_반환한다() throws Exception {
        // given: 8장 확정 사양 - profileImageUrl까지 3필드
        given(userProfileService.findProfiles(anyList())).willReturn(List.of(
            new UserProfileResponse(1L, "민서", "http://img/1"),
            new UserProfileResponse(2L, "길동", "http://img/2")
        ));

        // when & then
        mockMvc.perform(get("/api/v1/users/profiles").param("ids", "1,2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].id").exists())
            .andExpect(jsonPath("$.data[0].nickname").exists())
            .andExpect(jsonPath("$.data[0].profileImageUrl").exists());
    }

    @Test
    void ids_파라미터가_없으면_400을_반환한다() throws Exception {
        // when & then: 청사진 2-1 "ids 누락 -> 400". 현재 GlobalExceptionHandler에
        // MissingServletRequestParameterException 핸들러가 없어 500으로 새는 공백을 이번에 막는다.
        mockMvc.perform(get("/api/v1/users/profiles"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void ids에_숫자가_아닌_값이_섞이면_400을_반환한다() throws Exception {
        // when & then: MethodArgumentTypeMismatchException 핸들러가 없으면 500으로 샌다.
        mockMvc.perform(get("/api/v1/users/profiles").param("ids", "1,abc,3"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void 서비스가_TOO_MANY_USER_IDS를_던지면_400과_USER_004를_반환한다() throws Exception {
        // given
        given(userProfileService.findProfiles(anyList()))
            .willThrow(new BusinessException(UserErrorCode.TOO_MANY_USER_IDS));

        // when & then
        mockMvc.perform(get("/api/v1/users/profiles").param("ids", "1,2,3"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("USER_004"));
    }
}
