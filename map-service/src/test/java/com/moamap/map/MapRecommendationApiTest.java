package com.moamap.map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 추천 API가 실제 요청-응답 경로에서 동작하는지 검증한다.
 * 단위 테스트가 알고리즘을 다루므로 여기서는 계약(상태 코드·응답 모양·제외 규칙)에 집중한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MapRecommendationApiTest {

    private static final String USER_HEADER = "X-User-Id";
    private static final long ME = 1L;
    private static final long OTHER = 2L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("참여한 지도의 태그와 겹치는 커뮤니티 지도를 추천한다")
    void recommendsMapsMatchingMyTags() throws Exception {
        createMap(ME, "내 맛집 지도", List.of("맛집", "카페"));
        createMap(OTHER, "다른 맛집 지도", List.of("맛집"));
        createMap(OTHER, "캠핑 지도", List.of("캠핑"));

        mockMvc.perform(get("/api/v1/maps/recommendations").header(USER_HEADER, ME).param("size", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonPath("$.data[0].name").value("다른 맛집 지도"))
            .andExpect(jsonPath("$.data[0].reason").value(containsString("맛집")));
    }

    @Test
    @DisplayName("내가 만들었거나 참여 중인 지도는 추천에 나오지 않는다")
    void excludesMyOwnMaps() throws Exception {
        createMap(ME, "내 지도", List.of("맛집"));
        createMap(OTHER, "남의 지도", List.of("맛집"));

        mockMvc.perform(get("/api/v1/maps/recommendations").header(USER_HEADER, ME))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonPath("$.data[0].name").value("남의 지도"));
    }

    @Test
    @DisplayName("참여 이력이 없어도 인기 지도로 채워 빈 목록을 주지 않는다")
    void fallsBackForNewUser() throws Exception {
        createMap(OTHER, "인기 지도", List.of("맛집"));

        mockMvc.perform(get("/api/v1/maps/recommendations").header(USER_HEADER, 999L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonPath("$.data[0].name").value("인기 지도"))
            .andExpect(jsonPath("$.data[0].reason").value("지금 많이 찾는 지도예요"));
    }

    @Test
    @DisplayName("로그인하지 않아도 조회할 수 있다")
    void worksWithoutLogin() throws Exception {
        createMap(OTHER, "공개 지도", List.of("맛집"));

        mockMvc.perform(get("/api/v1/maps/recommendations"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    @DisplayName("프라이빗 지도는 추천 대상에서 제외한다")
    void excludesPrivateMaps() throws Exception {
        createMap(OTHER, "비공개 지도", "PRIVATE", List.of("맛집"));

        mockMvc.perform(get("/api/v1/maps/recommendations").header(USER_HEADER, ME))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @DisplayName("추천할 지도가 없으면 빈 목록을 반환한다")
    void returnsEmptyWhenNothingToRecommend() throws Exception {
        mockMvc.perform(get("/api/v1/maps/recommendations").header(USER_HEADER, ME))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(0)));
    }

    private void createMap(long ownerId, String name, List<String> tags) throws Exception {
        createMap(ownerId, name, "PUBLIC", tags);
    }

    private void createMap(long ownerId, String name, String visibility, List<String> tags) throws Exception {
        mockMvc.perform(post("/api/v1/maps")
                .header(USER_HEADER, ownerId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody(name, visibility, tags)))
            .andExpect(status().isCreated());
    }

    private byte[] createBody(String name, String visibility, List<String> tags) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("description", "설명");
        body.put("visibility", visibility);
        body.put("tags", tags);
        return objectMapper.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
    }
}
