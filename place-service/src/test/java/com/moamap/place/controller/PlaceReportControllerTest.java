package com.moamap.place.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moamap.place.dto.ReportResponse;
import com.moamap.place.service.PlaceReportService;
import com.moamap.place.service.ReportedPlaceService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PlaceReportController.class)
class PlaceReportControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private PlaceReportService placeReportService;
    @MockitoBean private ReportedPlaceService reportedPlaceService;

    @Test
    void 장소를_신고하면_201과_접수_정보를_준다() throws Exception {
        given(placeReportService.report(eq(7L), eq(2L), any()))
            .willReturn(new ReportResponse(30L, LocalDateTime.of(2026, 10, 24, 12, 0)));

        mockMvc.perform(post("/api/v1/places/7/reports").header("X-User-Id", 2L)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"FALSE_INFO\",\"detail\":\"폐업했어요\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.reportId").value(30));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"reason\":\"CLOSED\"}", "{\"reason\":\"OTHER\"}"})
    void 사유가_없거나_잘못됐거나_기타인데_내용이_없으면_400이다(String body) throws Exception {
        mockMvc.perform(post("/api/v1/places/7/reports").header("X-User-Id", 2L)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(placeReportService);
    }

    @Test
    void 신고된_장소_목록은_mapId가_없으면_400이다() throws Exception {
        mockMvc.perform(get("/api/v1/places/reports").header("X-User-Id", 1L))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(reportedPlaceService);
    }
}
