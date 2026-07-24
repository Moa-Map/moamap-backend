package com.moamap.user.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moamap.user.user.dto.MyPageResponse;
import com.moamap.user.user.dto.UpdateMyPageRequest;
import com.moamap.user.user.entity.Role;
import com.moamap.user.user.exception.UserNotFoundException;
import com.moamap.user.user.service.MyPageService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MyPageController.class)
class MyPageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MyPageService myPageService;

    private static MyPageResponse 응답(Long id, String nickname, String email, String profileImageUrl,
                                      String introduction) {
        return new MyPageResponse(id, nickname, email, profileImageUrl, "kakao", Role.USER,
                Instant.now(), Instant.now(), introduction);
    }

    @Test
    void 마이페이지_조회에_성공하면_사용자_정보를_반환한다() throws Exception {
        given(myPageService.getMyPage(1L)).willReturn(
                응답(1L, "길동", "a@b.com", "http://img", "안녕하세요"));

        mockMvc.perform(get("/api/v1/users/me").header("X-User-Id", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1L))
                .andExpect(jsonPath("$.data.nickname").value("길동"))
                .andExpect(jsonPath("$.data.email").value("a@b.com"))
                .andExpect(jsonPath("$.data.profileImageUrl").value("http://img"))
                .andExpect(jsonPath("$.data.provider").value("kakao"))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.introduction").value("안녕하세요"));
    }

    @Test
    void 자기소개가_없는_사용자를_조회하면_자기소개는_null이다() throws Exception {
        given(myPageService.getMyPage(2L)).willReturn(
                응답(2L, "길동", "a@b.com", "http://img", null));

        mockMvc.perform(get("/api/v1/users/me").header("X-User-Id", 2L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.introduction").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void 존재하지_않는_사용자를_조회하면_404를_반환한다() throws Exception {
        given(myPageService.getMyPage(999L)).willThrow(new UserNotFoundException("사용자를 찾을 수 없습니다."));

        mockMvc.perform(get("/api/v1/users/me").header("X-User-Id", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("USER_003"));
    }

    @Test
    void 닉네임과_프로필_이미지_수정에_성공하면_변경된_정보를_반환한다() throws Exception {
        given(myPageService.updateMyPage(eq(1L), any(UpdateMyPageRequest.class))).willReturn(
                응답(1L, "새닉네임", "a@b.com", "http://new", null));

        mockMvc.perform(patch("/api/v1/users/me")
                        .header("X-User-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"새닉네임\",\"profileImageUrl\":\"http://new\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("새닉네임"))
                .andExpect(jsonPath("$.data.profileImageUrl").value("http://new"));
    }

    @Test
    void 닉네임이_공백이면_400을_반환한다() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me")
                        .header("X-User-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 닉네임이_30자를_초과하면_400을_반환한다() throws Exception {
        String tooLong = "가".repeat(31);

        mockMvc.perform(patch("/api/v1/users/me")
                        .header("X-User-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 존재하지_않는_사용자를_수정하면_404를_반환한다() throws Exception {
        given(myPageService.updateMyPage(eq(999L), any(UpdateMyPageRequest.class)))
                .willThrow(new UserNotFoundException("사용자를 찾을 수 없습니다."));

        mockMvc.perform(patch("/api/v1/users/me")
                        .header("X-User-Id", 999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"새닉네임\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("USER_003"));
    }

    @Test
    void 이메일_수정에_성공하면_변경된_이메일을_반환한다() throws Exception {
        given(myPageService.updateMyPage(eq(1L), any(UpdateMyPageRequest.class))).willReturn(
                응답(1L, "길동", "new@b.com", "http://img", null));

        mockMvc.perform(patch("/api/v1/users/me")
                        .header("X-User-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"new@b.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("new@b.com"));
    }

    @Test
    void 이메일_형식이_올바르지_않으면_400을_반환한다() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me")
                        .header("X-User-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 자기소개_수정에_성공하면_변경된_자기소개를_반환한다() throws Exception {
        given(myPageService.updateMyPage(eq(1L), any(UpdateMyPageRequest.class))).willReturn(
                응답(1L, "길동", "a@b.com", "http://img", "안녕하세요"));

        mockMvc.perform(patch("/api/v1/users/me")
                        .header("X-User-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"introduction\":\"안녕하세요\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.introduction").value("안녕하세요"));
    }
}
