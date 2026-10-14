package com.moamap.user.notice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moamap.common.exception.BusinessException;
import com.moamap.common.exception.CommonErrorCode;
import com.moamap.user.exception.UserErrorCode;
import com.moamap.user.notice.dto.NoticeResponse;
import com.moamap.user.notice.service.NoticeAdminService;
import com.moamap.user.notice.service.NoticeService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 입력 검증과 응답 형식. 비즈니스 규칙은 NoticeServiceTest가 다룬다.
 */
@WebMvcTest({NoticeController.class, NoticeAdminController.class})
class NoticeControllerTest {

    private static final Instant AT = Instant.parse("2026-10-15T00:00:00Z");

    @Autowired private MockMvc mockMvc;
    @MockitoBean private NoticeService noticeService;
    @MockitoBean private NoticeAdminService noticeAdminService;

    @Test
    void 목록은_로그인_없이_페이지_형식으로_내려준다() throws Exception {
        given(noticeService.getNotices(any())).willReturn(new PageImpl<>(
                List.of(new NoticeResponse(1L, "점검 안내", "본문", AT, AT)), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/notices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].noticeId").value(1))
                .andExpect(jsonPath("$.data.content[0].title").value("점검 안내"))
                .andExpect(jsonPath("$.data.content[0].createdAt").value("2026-10-15T00:00:00Z"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void 없는_공지는_404다() throws Exception {
        given(noticeService.getNotice(9L)).willThrow(new BusinessException(UserErrorCode.NOTICE_NOT_FOUND));

        mockMvc.perform(get("/api/v1/notices/9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("USER_011"));
    }

    @Test
    void 등록에_성공하면_201이다() throws Exception {
        given(noticeAdminService.create(eq(1L), any())).willReturn(new NoticeResponse(5L, "제목", "본문", AT, AT));

        mockMvc.perform(post("/api/v1/admin/notices").header("X-User-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"제목\",\"content\":\"본문\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.noticeId").value(5));
    }

    @Test
    void 제목이_비어_있으면_등록할_수_없다() throws Exception {
        mockMvc.perform(post("/api/v1/admin/notices").header("X-User-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"   \",\"content\":\"본문\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(noticeAdminService);
    }

    @Test
    void 제목이_100자를_넘으면_등록할_수_없다() throws Exception {
        String title = "가".repeat(101);
        mockMvc.perform(post("/api/v1/admin/notices").header("X-User-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"content\":\"본문\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 본문이_5000자를_넘으면_등록할_수_없다() throws Exception {
        String content = "가".repeat(5001);
        mockMvc.perform(post("/api/v1/admin/notices").header("X-User-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"제목\",\"content\":\"" + content + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 수정할_값을_하나도_보내지_않으면_거부한다() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/notices/1").header("X-User-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(noticeAdminService);
    }

    @Test
    void 수정할_제목을_빈칸으로_보내면_거부한다() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/notices/1").header("X-User-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 관리자_API는_인증_정보가_없으면_401이다() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/notices/1"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(noticeAdminService);
    }

    @Test
    void 관리자가_아니면_403이다() throws Exception {
        given(noticeAdminService.create(eq(2L), any())).willThrow(new BusinessException(CommonErrorCode.ACCESS_DENIED));

        mockMvc.perform(post("/api/v1/admin/notices").header("X-User-Id", 2L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"제목\",\"content\":\"본문\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("COMMON_004"));
    }
}
