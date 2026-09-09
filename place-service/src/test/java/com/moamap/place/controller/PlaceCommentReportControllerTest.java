package com.moamap.place.controller;

import java.time.LocalDateTime;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moamap.common.exception.BusinessException;
import com.moamap.place.dto.PageResponse;
import com.moamap.place.dto.PlaceCommentReportRequest;
import com.moamap.place.dto.PlaceCommentReportResponse;
import com.moamap.place.dto.ReportedCommentResponse;
import com.moamap.place.entity.CommentReportReason;
import com.moamap.place.exception.PlaceErrorCode;
import com.moamap.place.service.PlaceCommentReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 라우팅/요청 바인딩/상태 코드만 검증한다. 신고 권한·중복 판단은 PlaceCommentReportServiceTest에서 검증한다.
 */
@WebMvcTest(PlaceCommentReportController.class)
class PlaceCommentReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PlaceCommentReportService placeCommentReportService;

    private PlaceCommentReportRequest request() {
        return new PlaceCommentReportRequest(CommentReportReason.SPAM, "다른 가게 홍보입니다.");
    }

    @Test
    void report는_성공하면_201과_접수_결과를_반환한다() throws Exception {
        // given
        given(placeCommentReportService.report(eq(1L), eq(5L), eq(2L), any()))
            .willReturn(new PlaceCommentReportResponse(5L, CommentReportReason.SPAM, LocalDateTime.now()));

        // when & then
        mockMvc.perform(post("/api/v1/places/{placeId}/comments/{commentId}/reports", 1L, 5L)
                .header("X-User-Id", 2L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.commentId").value(5))
            .andExpect(jsonPath("$.data.reason").value("SPAM"))
            // 신고자에게 누적 수를 노출하지 않는다는 계약
            .andExpect(jsonPath("$.data.reportCount").doesNotExist());
    }

    @Test
    void report는_신고_사유가_없으면_400을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(post("/api/v1/places/{placeId}/comments/{commentId}/reports", 1L, 5L)
                .header("X-User-Id", 2L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"detail\":\"사유를 안 골랐다\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void report는_이미_신고한_댓글이면_409를_반환한다() throws Exception {
        // given
        given(placeCommentReportService.report(eq(1L), eq(5L), eq(2L), any()))
            .willThrow(new BusinessException(PlaceErrorCode.ALREADY_REPORTED_COMMENT));

        // when & then
        mockMvc.perform(post("/api/v1/places/{placeId}/comments/{commentId}/reports", 1L, 5L)
                .header("X-User-Id", 2L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request())))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("PLACE_019"));
    }

    @Test
    void report는_본인_댓글이면_400을_반환한다() throws Exception {
        // given
        given(placeCommentReportService.report(eq(1L), eq(5L), eq(2L), any()))
            .willThrow(new BusinessException(PlaceErrorCode.CANNOT_REPORT_OWN_COMMENT));

        // when & then
        mockMvc.perform(post("/api/v1/places/{placeId}/comments/{commentId}/reports", 1L, 5L)
                .header("X-User-Id", 2L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("PLACE_020"));
    }

    @Test
    void getReported는_성공하면_200과_신고_누적_목록을_반환한다() throws Exception {
        // given
        ReportedCommentResponse row = new ReportedCommentResponse(
            5L, 1L, "스타벅스 강남점", 7L, "광고글", 3, 3, LocalDateTime.now());
        given(placeCommentReportService.findReported(eq(10L), eq(2L), any()))
            .willReturn(PageResponse.from(new PageImpl<>(List.of(row), PageRequest.of(0, 20), 1)));

        // when & then
        mockMvc.perform(get("/api/v1/places/comments/reported")
                .param("mapId", "10")
                .header("X-User-Id", 2L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0].commentId").value(5))
            .andExpect(jsonPath("$.data.content[0].placeName").value("스타벅스 강남점"))
            // 누적 수는 관리자 조회 응답에만 담긴다
            .andExpect(jsonPath("$.data.content[0].reportCount").value(3));
    }

    @Test
    void getReported는_방장_관리자가_아니면_403을_반환한다() throws Exception {
        // given
        given(placeCommentReportService.findReported(eq(10L), eq(2L), any()))
            .willThrow(new BusinessException(PlaceErrorCode.NOT_REVIEWER));

        // when & then
        mockMvc.perform(get("/api/v1/places/comments/reported")
                .param("mapId", "10")
                .header("X-User-Id", 2L))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("PLACE_005"));
    }

    @Test
    void getReported는_mapId가_없으면_400을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/places/comments/reported")
                .header("X-User-Id", 2L))
            .andExpect(status().isBadRequest());
    }
}
