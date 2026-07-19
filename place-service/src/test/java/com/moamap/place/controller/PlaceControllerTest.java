package com.moamap.place.controller;

import java.math.BigDecimal;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moamap.common.exception.BusinessException;
import com.moamap.place.dto.PageResponse;
import com.moamap.place.dto.PlaceCreateRequest;
import com.moamap.place.dto.PlaceResponse;
import com.moamap.place.dto.PlaceUpdateRequest;
import com.moamap.place.entity.PlaceSourceType;
import com.moamap.place.entity.PlaceStatus;
import com.moamap.place.exception.PlaceErrorCode;
import com.moamap.place.service.PlaceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 라우팅/요청 바인딩/상태 코드가 배선대로 동작하는지만 검증한다.
 * 승인 상태 결정, 권한 분기 같은 비즈니스 로직 자체는 PlaceServiceTest에서 이미 검증한다.
 */
@WebMvcTest(PlaceController.class)
class PlaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PlaceService placeService;

    private PlaceResponse response() {
        return new PlaceResponse(
            1L, "스타벅스 강남점", "서울 강남구 테헤란로 1", "서울 강남구 테헤란로 1",
            BigDecimal.valueOf(37.497852), BigDecimal.valueOf(127.027618), "카페", "26338954",
            PlaceSourceType.KAKAO_SEARCH, null, null, 10L, 1L, PlaceStatus.APPROVED,
            null, 0, null, null, null, null
        );
    }

    private PlaceCreateRequest createRequest() {
        return new PlaceCreateRequest(
            "스타벅스 강남점", "서울 강남구 테헤란로 1", "서울 강남구 테헤란로 1",
            BigDecimal.valueOf(37.497852), BigDecimal.valueOf(127.027618), "카페", "26338954",
            PlaceSourceType.KAKAO_SEARCH, null, null, 10L
        );
    }

    @Test
    void create는_성공하면_201과_등록된_장소를_반환한다() throws Exception {
        // given
        given(placeService.create(any(), eq(1L))).willReturn(response());

        // when & then
        mockMvc.perform(post("/places")
                .header("X-User-Id", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.name").value("스타벅스 강남점"))
            .andExpect(jsonPath("$.data.status").value("APPROVED"));
        verify(placeService).create(any(), eq(1L));
    }

    @Test
    void create는_필수값이_없으면_400을_반환한다() throws Exception {
        // given: name이 비어있음(@NotBlank 위반)
        String invalidBody = """
            {"name": "", "lat": 37.5, "lng": 127.0, "kakaoPlaceId": "1",
             "sourceType": "KAKAO_SEARCH", "mapId": 10}
            """;

        // when & then
        mockMvc.perform(post("/places")
                .header("X-User-Id", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidBody))
            .andExpect(status().isBadRequest());
        verify(placeService, org.mockito.Mockito.never()).create(any(), any());
    }

    @Test
    void getById는_존재하면_200과_장소를_반환한다() throws Exception {
        // given
        given(placeService.findById(1L)).willReturn(response());

        // when & then
        mockMvc.perform(get("/places/{id}", 1L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(1))
            .andExpect(jsonPath("$.data.name").value("스타벅스 강남점"));
    }

    @Test
    void getById는_존재하지_않으면_404를_반환한다() throws Exception {
        // given: GlobalExceptionHandler가 BusinessException을 errorCode의 status로 변환하는지 검증
        given(placeService.findById(999L)).willThrow(new BusinessException(PlaceErrorCode.PLACE_NOT_FOUND));

        // when & then
        mockMvc.perform(get("/places/{id}", 999L))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("PLACE_001"));
    }

    @Test
    void getAll은_mapId로_페이지_결과를_반환한다() throws Exception {
        // given
        PageResponse<PlaceResponse> page = new PageResponse<>(List.of(response()), 0, 20, 1, 1, true);
        given(placeService.findAllByMapId(eq(10L), any())).willReturn(page);

        // when & then
        mockMvc.perform(get("/places").param("mapId", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0].id").value(1))
            .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void getPending은_요청자_헤더와_mapId를_그대로_서비스에_전달한다() throws Exception {
        // given
        PageResponse<PlaceResponse> page = new PageResponse<>(List.of(response()), 0, 20, 1, 1, true);
        given(placeService.findPendingByMapId(eq(10L), eq(2L), any())).willReturn(page);

        // when & then
        mockMvc.perform(get("/places/pending").param("mapId", "10").header("X-User-Id", 2L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0].id").value(1));
        verify(placeService).findPendingByMapId(eq(10L), eq(2L), any());
    }

    @Test
    void update는_성공하면_200과_수정된_장소를_반환한다() throws Exception {
        // given
        PlaceUpdateRequest request = new PlaceUpdateRequest("새 이름", null, null, null, null, null, null);
        given(placeService.update(eq(1L), eq(1L), any())).willReturn(response());

        // when & then
        mockMvc.perform(patch("/places/{id}", 1L)
                .header("X-User-Id", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name").value("스타벅스 강남점"));
    }

    @Test
    void delete는_성공하면_204를_반환한다() throws Exception {
        // when & then
        mockMvc.perform(delete("/places/{id}", 1L).header("X-User-Id", 1L))
            .andExpect(status().isNoContent());
        verify(placeService).delete(1L, 1L);
    }

    @Test
    void approve는_성공하면_200과_승인된_장소를_반환한다() throws Exception {
        // given
        given(placeService.approve(1L, 2L)).willReturn(response());

        // when & then
        mockMvc.perform(patch("/places/{id}/approve", 1L).header("X-User-Id", 2L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    @Test
    void reject는_성공하면_200과_반려된_장소를_반환한다() throws Exception {
        // given
        given(placeService.reject(1L, 2L)).willReturn(response());

        // when & then
        mockMvc.perform(patch("/places/{id}/reject", 1L).header("X-User-Id", 2L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(1));
    }
}
