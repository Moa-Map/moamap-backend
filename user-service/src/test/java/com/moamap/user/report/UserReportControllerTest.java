package com.moamap.user.report;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moamap.user.report.controller.UserReportController;
import com.moamap.user.report.dto.ReportResponse;
import com.moamap.user.report.service.UserReportService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserReportController.class)
class UserReportControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private UserReportService userReportService;

    @Test
    void 신고하면_201과_접수_정보를_준다() throws Exception {
        given(userReportService.report(eq(1L), eq(2L), any()))
                .willReturn(new ReportResponse(9L, Instant.parse("2026-10-24T00:00:00Z")));

        mockMvc.perform(post("/api/v1/users/2/reports").header("X-User-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"ABUSE\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.reportId").value(9));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"reason\":\"UNKNOWN\"}",
            "{\"reason\":\"OTHER\"}",
            "{\"reason\":\"OTHER\",\"detail\":\"  \"}",
            "{\"reason\":\"BLOCKED\"}"
    })
    void 사유가_없거나_잘못됐거나_서버_전용이거나_기타인데_내용이_없으면_400이다(String body) throws Exception {
        mockMvc.perform(post("/api/v1/users/2/reports").header("X-User-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userReportService);
    }

    @Test
    void 로그인하지_않으면_401이다() throws Exception {
        mockMvc.perform(post("/api/v1/users/2/reports")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"ABUSE\"}"))
                .andExpect(status().isUnauthorized());
    }
}
