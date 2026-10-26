package com.moamap.map.controller;

import java.math.BigDecimal;

import com.moamap.common.exception.BusinessException;
import com.moamap.map.dto.PublicRestroomDetailResponse;
import com.moamap.map.dto.PublicRestroomListResponse;
import com.moamap.map.dto.PublicRestroomMarkerResponse;
import com.moamap.map.exception.MapErrorCode;
import com.moamap.map.service.PublicRestroomQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 라우팅/파라미터 바인딩/상태 코드만 검증한다. 500건 상한·truncated 계산 등 비즈니스 로직은
 * PublicRestroomQueryServiceTest가 검증한다. 비로그인 공개 API이므로 X-User-Id 헤더 없이 호출한다.
 */
@WebMvcTest(PublicRestroomController.class)
class PublicRestroomControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PublicRestroomQueryService queryService;

    @Test
    void 목록_조회는_bbox_파라미터가_모두_있으면_200을_반환한다() throws Exception {
        // given
        PublicRestroomMarkerResponse marker = new PublicRestroomMarkerResponse(
            1L, "화장실", BigDecimal.valueOf(37.5), BigDecimal.valueOf(127.0), "공중화장실", false, "상시");
        given(queryService.listMarkers(any())).willReturn(
            new PublicRestroomListResponse(java.util.List.of(marker), false));

        // when & then
        mockMvc.perform(get("/api/v1/maps/official/restrooms")
                .param("swLat", "37.4").param("swLng", "126.9")
                .param("neLat", "37.6").param("neLng", "127.1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.restrooms[0].id").value(1))
            .andExpect(jsonPath("$.data.truncated").value(false));
    }

    @Test
    void 목록_조회는_파라미터가_하나라도_없으면_400을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/maps/official/restrooms")
                .param("swLat", "37.4").param("swLng", "126.9")
                .param("neLat", "37.6"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void 목록_조회는_위도가_범위를_벗어나면_400을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/maps/official/restrooms")
                .param("swLat", "91").param("swLng", "126.9")
                .param("neLat", "37.6").param("neLng", "127.1"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void 목록_조회는_경도가_범위를_벗어나면_400을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/maps/official/restrooms")
                .param("swLat", "37.4").param("swLng", "126.9")
                .param("neLat", "37.6").param("neLng", "181"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void 상세_조회는_존재하면_200을_반환한다() throws Exception {
        // given
        PublicRestroomDetailResponse detail = new PublicRestroomDetailResponse(
            1L, "MNG-1", "화장실", "공중화장실", "공공", "서울 어딘가", "서울 어딘가",
            BigDecimal.valueOf(37.5), BigDecimal.valueOf(127.0),
            (short) 1, (short) 1, (short) 0, (short) 0, (short) 0, (short) 0,
            (short) 1, (short) 0, (short) 0,
            "상시", null, false, null, false, null, false, null, null, null, null, null, null);
        given(queryService.getDetail(1L)).willReturn(detail);

        // when & then
        mockMvc.perform(get("/api/v1/maps/official/restrooms/{id}", 1L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    void 상세_조회는_존재하지_않으면_404와_MAP_021을_반환한다() throws Exception {
        // given
        given(queryService.getDetail(999L)).willThrow(new BusinessException(MapErrorCode.RESTROOM_NOT_FOUND));

        // when & then
        mockMvc.perform(get("/api/v1/maps/official/restrooms/{id}", 999L))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("MAP_021"));
    }

    @Test
    void 인증_헤더_없이도_목록_조회가_통과한다() throws Exception {
        // given
        given(queryService.listMarkers(any())).willReturn(new PublicRestroomListResponse(java.util.List.of(), false));

        // when & then: X-User-Id 헤더를 붙이지 않는다 (비로그인 공개 API)
        mockMvc.perform(get("/api/v1/maps/official/restrooms")
                .param("swLat", "37.4").param("swLng", "126.9")
                .param("neLat", "37.6").param("neLng", "127.1"))
            .andExpect(status().isOk());
    }
}
