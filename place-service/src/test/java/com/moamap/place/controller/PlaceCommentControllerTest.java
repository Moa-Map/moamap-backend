package com.moamap.place.controller;

import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moamap.common.exception.BusinessException;
import com.moamap.place.dto.PageResponse;
import com.moamap.place.dto.PlaceCommentCreateRequest;
import com.moamap.place.dto.PlaceCommentResponse;
import com.moamap.place.dto.PlaceCommentUpdateRequest;
import com.moamap.place.exception.PlaceErrorCode;
import com.moamap.place.service.PlaceCommentPhotoService;
import com.moamap.place.service.PlaceCommentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 라우팅/요청 바인딩/상태 코드만 검증한다. 평균별점 갱신, 소유권 판단 같은
 * 비즈니스 로직 자체는 PlaceCommentServiceTest에서 이미 검증한다.
 */
@WebMvcTest(PlaceCommentController.class)
class PlaceCommentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PlaceCommentService placeCommentService;

    @MockitoBean
    private PlaceCommentPhotoService placeCommentPhotoService;

    private PlaceCommentResponse response() {
        return new PlaceCommentResponse(1L, 1L, 2L, 5, "최고예요", List.of("https://img/1.jpg"), null, null);
    }

    @Test
    void create는_성공하면_201과_생성된_댓글을_반환한다() throws Exception {
        // given
        PlaceCommentCreateRequest request = new PlaceCommentCreateRequest(5, "최고예요", List.of("https://img/1.jpg"));
        given(placeCommentService.create(eq(1L), eq(2L), any())).willReturn(response());

        // when & then
        mockMvc.perform(post("/api/v1/places/{placeId}/comments", 1L)
                .header("X-User-Id", 2L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.rating").value(5))
            .andExpect(jsonPath("$.data.content").value("최고예요"));
    }

    @Test
    void create는_rating이_범위를_벗어나면_400을_반환한다() throws Exception {
        // given: rating이 5 초과(@Max 위반)
        String invalidBody = """
            {"rating": 6, "content": "최고예요"}
            """;

        // when & then
        mockMvc.perform(post("/api/v1/places/{placeId}/comments", 1L)
                .header("X-User-Id", 2L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidBody))
            .andExpect(status().isBadRequest());
    }

    @Test
    void getAll은_페이지_결과를_반환한다() throws Exception {
        // given
        PageResponse<PlaceCommentResponse> page = new PageResponse<>(List.of(response()), 0, 20, 1, 1, true);
        given(placeCommentService.findAllByPlaceId(eq(1L), any())).willReturn(page);

        // when & then
        mockMvc.perform(get("/api/v1/places/{placeId}/comments", 1L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0].rating").value(5));
    }

    @Test
    void update는_성공하면_200과_수정된_댓글을_반환한다() throws Exception {
        // given
        PlaceCommentUpdateRequest request = new PlaceCommentUpdateRequest(4, "괜찮아요", null);
        given(placeCommentService.update(eq(1L), eq(5L), eq(2L), any())).willReturn(response());

        // when & then
        mockMvc.perform(patch("/api/v1/places/{placeId}/comments/{commentId}", 1L, 5L)
                .header("X-User-Id", 2L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    void delete는_성공하면_success_envelope을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(delete("/api/v1/places/{placeId}/comments/{commentId}", 1L, 5L).header("X-User-Id", 2L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void delete는_본인_댓글이_아니면_403을_반환한다() throws Exception {
        // given: GlobalExceptionHandler가 BusinessException을 errorCode의 status로 변환하는지 검증
        org.mockito.BDDMockito.willThrow(new BusinessException(PlaceErrorCode.NOT_COMMENT_OWNER))
            .given(placeCommentService).delete(1L, 5L, 3L);

        // when & then
        mockMvc.perform(delete("/api/v1/places/{placeId}/comments/{commentId}", 1L, 5L).header("X-User-Id", 3L))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("PLACE_012"));
    }
}
