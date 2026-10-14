package com.moamap.user.inquiry.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moamap.common.exception.BusinessException;
import com.moamap.user.exception.UserErrorCode;
import com.moamap.user.inquiry.dto.InquiryCreateResponse;
import com.moamap.user.inquiry.service.InquiryAdminService;
import com.moamap.user.inquiry.service.InquiryService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 입력 검증과 응답 형식. 비즈니스 규칙은 InquiryServiceTest가 다룬다.
 */
@WebMvcTest({InquiryController.class, InquiryAdminController.class})
class InquiryControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private InquiryService inquiryService;
    @MockitoBean private InquiryAdminService inquiryAdminService;

    @Test
    void 등록에_성공하면_201이고_보낸_이메일과_내용은_되돌려주지_않는다() throws Exception {
        given(inquiryService.create(eq(1L), any()))
                .willReturn(new InquiryCreateResponse(7L, Instant.parse("2026-10-15T00:00:00Z")));

        mockMvc.perform(post("/api/v1/inquiries").header("X-User-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("TECHNICAL", "user@example.com", "문의 내용")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.inquiryId").value(7))
                .andExpect(jsonPath("$.data.replyEmail").doesNotExist())
                .andExpect(jsonPath("$.data.content").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-an-email", "a@b", "a b@example.com", ""})
    void 이메일_형식이_아니면_거부한다(String email) throws Exception {
        mockMvc.perform(post("/api/v1/inquiries").header("X-User-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("TECHNICAL", email, "문의 내용")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(inquiryService);
    }

    @Test
    void 없는_문의_유형은_500이_아니라_400이다() throws Exception {
        mockMvc.perform(post("/api/v1/inquiries").header("X-User-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("HACKING", "user@example.com", "문의 내용")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_001"));
    }

    @Test
    void 깨진_JSON은_500이_아니라_400이다() throws Exception {
        mockMvc.perform(post("/api/v1/inquiries").header("X-User-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 문의_유형이_없으면_거부한다() throws Exception {
        mockMvc.perform(post("/api/v1/inquiries").header("X-User-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"replyEmail\":\"user@example.com\",\"content\":\"내용\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 내용이_2000자를_넘으면_거부한다() throws Exception {
        mockMvc.perform(post("/api/v1/inquiries").header("X-User-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("OTHER", "user@example.com", "가".repeat(2001))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 내용이_빈칸이면_거부한다() throws Exception {
        mockMvc.perform(post("/api/v1/inquiries").header("X-User-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("OTHER", "user@example.com", "   ")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 하루_한도를_넘으면_429다() throws Exception {
        given(inquiryService.create(eq(1L), any())).willThrow(new BusinessException(UserErrorCode.TOO_MANY_INQUIRIES));

        mockMvc.perform(post("/api/v1/inquiries").header("X-User-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("OTHER", "user@example.com", "문의")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("USER_013"));
    }

    @Test
    void 로그인하지_않으면_문의할_수_없다() throws Exception {
        mockMvc.perform(post("/api/v1/inquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("OTHER", "user@example.com", "문의")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 관리자_목록에_없는_상태값을_보내면_400이다() throws Exception {
        mockMvc.perform(get("/api/v1/admin/inquiries").header("X-User-Id", 1L).param("status", "DONE"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(inquiryAdminService);
    }

    @Test
    void 상태를_보내지_않고_변경하면_400이다() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/inquiries/1/status").header("X-User-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 관리자_API는_인증_정보가_없으면_401이다() throws Exception {
        mockMvc.perform(get("/api/v1/admin/inquiries/1"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(inquiryAdminService);
    }

    private static String body(String type, String email, String content) {
        return "{\"type\":\"" + type + "\",\"replyEmail\":\"" + email + "\",\"content\":\"" + content + "\"}";
    }
}
