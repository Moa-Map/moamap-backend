package com.moamap.place.controller;

import com.moamap.common.exception.BusinessException;
import com.moamap.place.dto.PlaceLikeResponse;
import com.moamap.place.exception.PlaceErrorCode;
import com.moamap.place.service.PlaceLikeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 라우팅/헤더 바인딩/상태 코드만 검증한다. 멱등성·권한은 서비스 테스트에서 다룬다.
 */
@WebMvcTest(PlaceLikeController.class)
class PlaceLikeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlaceLikeService placeLikeService;

    @Test
    void like는_성공하면_200과_현재_상태를_반환한다() throws Exception {
        // given
        given(placeLikeService.like(1L, 2L)).willReturn(PlaceLikeResponse.of(1L, 5, true));

        // when & then
        mockMvc.perform(post("/api/v1/places/{placeId}/likes", 1L).header("X-User-Id", 2L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.placeId").value(1))
            .andExpect(jsonPath("$.data.likeCount").value(5))
            .andExpect(jsonPath("$.data.liked").value(true));
    }

    @Test
    void unlike는_성공하면_200과_현재_상태를_반환한다() throws Exception {
        // given
        given(placeLikeService.unlike(1L, 2L)).willReturn(PlaceLikeResponse.of(1L, 4, false));

        // when & then
        mockMvc.perform(delete("/api/v1/places/{placeId}/likes", 1L).header("X-User-Id", 2L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.likeCount").value(4))
            .andExpect(jsonPath("$.data.liked").value(false));
    }

    /** 중복 요청은 409가 아니라 200이다. 프론트가 응답을 그대로 반영할 수 있어야 한다. */
    @Test
    void 이미_누른_상태에서_다시_눌러도_200이다() throws Exception {
        // given
        given(placeLikeService.like(1L, 2L)).willReturn(PlaceLikeResponse.of(1L, 5, true));

        // when & then
        mockMvc.perform(post("/api/v1/places/{placeId}/likes", 1L).header("X-User-Id", 2L))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/places/{placeId}/likes", 1L).header("X-User-Id", 2L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.liked").value(true));
    }

    @Test
    void 비멤버면_403을_반환한다() throws Exception {
        // given
        given(placeLikeService.like(1L, 2L)).willThrow(new BusinessException(PlaceErrorCode.NOT_MAP_MEMBER));

        // when & then
        mockMvc.perform(post("/api/v1/places/{placeId}/likes", 1L).header("X-User-Id", 2L))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("PLACE_002"));
    }

    @Test
    void 없는_장소면_404를_반환한다() throws Exception {
        // given
        given(placeLikeService.like(999L, 2L)).willThrow(new BusinessException(PlaceErrorCode.PLACE_NOT_FOUND));

        // when & then
        mockMvc.perform(post("/api/v1/places/{placeId}/likes", 999L).header("X-User-Id", 2L))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("PLACE_001"));
    }

    /** 게이트웨이가 토큰을 강제하지만, 직접 호출돼도 fail-closed로 막혀야 한다. */
    @Test
    void 헤더가_없으면_서비스가_받은_userId는_null이다() throws Exception {
        // given
        given(placeLikeService.like(1L, null))
            .willThrow(new BusinessException(com.moamap.common.exception.CommonErrorCode.UNAUTHORIZED));

        // when & then
        mockMvc.perform(post("/api/v1/places/{placeId}/likes", 1L))
            .andExpect(status().isUnauthorized());
    }
}
