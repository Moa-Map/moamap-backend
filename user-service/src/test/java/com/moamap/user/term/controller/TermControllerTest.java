package com.moamap.user.term.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moamap.common.exception.BusinessException;
import com.moamap.user.exception.UserErrorCode;
import com.moamap.user.term.dto.TermAgreementStatusResponse;
import com.moamap.user.term.dto.TermDetailResponse;
import com.moamap.user.term.dto.TermSummaryResponse;
import com.moamap.user.term.entity.TermKind;
import com.moamap.user.term.service.TermAgreementService;
import com.moamap.user.term.service.TermService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 입력 검증과 응답 형식. 동의 규칙은 TermAgreementServiceTest가 다룬다.
 */
@WebMvcTest({TermController.class, TermAgreementController.class})
class TermControllerTest {

    private static final TermSummaryResponse SERVICE =
            new TermSummaryResponse(4L, "SERVICE", "서비스 이용약관", "v2", TermKind.REQUIRED);

    @Autowired private MockMvc mockMvc;
    @MockitoBean private TermService termService;
    @MockitoBean private TermAgreementService termAgreementService;

    @Test
    void 약관_목록은_로그인_없이_볼_수_있다() throws Exception {
        given(termService.getActiveTerms()).willReturn(List.of(SERVICE));

        mockMvc.perform(get("/api/v1/terms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].termId").value(4))
                .andExpect(jsonPath("$.data[0].code").value("SERVICE"))
                .andExpect(jsonPath("$.data[0].kind").value("REQUIRED"))
                .andExpect(jsonPath("$.data[0].content").doesNotExist());
    }

    @Test
    void 약관_전문은_Markdown_본문을_준다() throws Exception {
        given(termService.getTerm(4L)).willReturn(new TermDetailResponse(4L, "SERVICE", "서비스 이용약관", "v2",
                TermKind.REQUIRED, true, "# 서비스 이용약관"));

        mockMvc.perform(get("/api/v1/terms/4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("# 서비스 이용약관"))
                .andExpect(jsonPath("$.data.active").value(true));
    }

    @Test
    void 없는_약관은_404다() throws Exception {
        given(termService.getTerm(9L)).willThrow(new BusinessException(UserErrorCode.TERM_NOT_FOUND));

        mockMvc.perform(get("/api/v1/terms/9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("USER_020"));
    }

    @Test
    void 내_동의_현황을_준다() throws Exception {
        given(termAgreementService.getMyStatus(1L)).willReturn(TermAgreementStatusResponse.of(List.of(SERVICE)));

        mockMvc.perform(get("/api/v1/terms/agreements/me").header("X-User-Id", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.needsAgreement").value(true))
                .andExpect(jsonPath("$.data.pendingTerms[0].termId").value(4));
    }

    @Test
    void 동의_현황과_동의는_인증이_필요하다() throws Exception {
        mockMvc.perform(get("/api/v1/terms/agreements/me"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/terms/agreements").contentType(MediaType.APPLICATION_JSON).content("{\"termIds\":[4]}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(termAgreementService);
    }

    @Test
    void 동의하면_남은_동의_대상이_없는_현황을_돌려준다() throws Exception {
        given(termAgreementService.agree(eq(1L), any())).willReturn(TermAgreementStatusResponse.of(List.of()));

        mockMvc.perform(post("/api/v1/terms/agreements").header("X-User-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"termIds\":[4, 2]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.needsAgreement").value(false))
                .andExpect(jsonPath("$.data.pendingTerms").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"termIds\":[]}",
            "{\"termIds\":[null]}",
            "{\"termIds\":[0]}",
            "{\"termIds\":[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21]}",
            "{\"termIds\":\"4\"}",
            "{broken"
    })
    void 동의_요청_형식이_잘못되면_400이다(String body) throws Exception {
        mockMvc.perform(post("/api/v1/terms/agreements").header("X-User-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_001"));

        verifyNoInteractions(termAgreementService);
    }

    @Test
    void 약관이_갱신됐으면_409다() throws Exception {
        given(termAgreementService.agree(eq(1L), any())).willThrow(new BusinessException(UserErrorCode.TERM_OUTDATED));

        mockMvc.perform(post("/api/v1/terms/agreements").header("X-User-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"termIds\":[1]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("USER_023"));
    }
}
