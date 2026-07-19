package com.moamap.place.controller;

import java.math.BigDecimal;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moamap.place.dto.InstagramExtractRequest;
import com.moamap.place.dto.PlaceCandidateResponse;
import com.moamap.place.service.PlaceExtractionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 라우팅/요청 검증만 확인한다. 추출/병합 로직 자체는 PlaceExtractionServiceTest에서 검증한다.
 */
@WebMvcTest(PlaceExtractionController.class)
class PlaceExtractionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PlaceExtractionService placeExtractionService;

    @Test
    void extract는_성공하면_200과_후보_목록을_반환한다() throws Exception {
        // given
        InstagramExtractRequest request = new InstagramExtractRequest("https://instagram.com/p/1", "북촌 프루 다녀왔어요");
        PlaceCandidateResponse candidate = new PlaceCandidateResponse(
            "1", "프루", "카페", "서울 종로구", "서울 종로구 북촌로",
            BigDecimal.valueOf(37.58), BigDecimal.valueOf(126.98),
            "http://place.map.kakao.com/1", "https://instagram.com/p/1"
        );
        given(placeExtractionService.extractFromInstagram(any())).willReturn(List.of(candidate));

        // when & then
        mockMvc.perform(post("/places/instagram-extractions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].name").value("프루"));
    }

    @Test
    void extract는_url이_없으면_400을_반환한다() throws Exception {
        // given: url이 비어있음(@NotBlank 위반)
        String invalidBody = """
            {"url": "", "description": "설명글"}
            """;

        // when & then
        mockMvc.perform(post("/places/instagram-extractions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidBody))
            .andExpect(status().isBadRequest());
    }
}
