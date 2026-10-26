package com.moamap.map;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moamap.map.place.PlaceClient;
import com.moamap.map.user.UserClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 로그 탭 API의 계약(상태 코드·응답 모양·검증 실패)을 검증한다.
 * 권한 분기 규칙 자체는 MapPostServiceTest·MapPostCommentServiceTest가 다룬다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MapPostApiTest {

    private static final String USER_HEADER = "X-User-Id";
    private static final long OWNER = 1L;
    private static final long OUTSIDER = 99L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserClient userClient;

    @MockitoBean
    private PlaceClient placeClient;

    @Test
    @DisplayName("게시물을 작성하면 201과 태그·사진이 함께 반환된다")
    void 게시물을_작성하면_201과_태그_사진이_함께_반환된다() throws Exception {
        long mapId = createMap();

        mockMvc.perform(post("/api/v1/maps/{mapId}/posts", mapId)
                .header(USER_HEADER, OWNER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(postBody("성수 카페 다녀왔어요"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.content").value("성수 카페 다녀왔어요"))
            .andExpect(jsonPath("$.data.imageUrls", hasSize(1)))
            .andExpect(jsonPath("$.data.placeTags[0].placeId").value(5))
            .andExpect(jsonPath("$.data.placeTags[0].name").value("블루보틀 성수점"));
    }

    @Test
    @DisplayName("커뮤니티 지도의 게시물 목록은 비로그인도 조회할 수 있다")
    void 커뮤니티_지도의_게시물_목록은_비로그인도_조회할_수_있다() throws Exception {
        long mapId = createMap();
        createPost(mapId, "성수 카페 다녀왔어요");

        mockMvc.perform(get("/api/v1/maps/{mapId}/posts", mapId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content", hasSize(1)));
    }

    @Test
    @DisplayName("비멤버는 커뮤니티 지도에도 게시물을 쓸 수 없다")
    void 비멤버는_커뮤니티_지도에도_게시물을_쓸_수_없다() throws Exception {
        long mapId = createMap();

        mockMvc.perform(post("/api/v1/maps/{mapId}/posts", mapId)
                .header(USER_HEADER, OUTSIDER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(postBody("몰래 쓰기"))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("MAP_002"));
    }

    @Test
    @DisplayName("본문이 비어 있으면 400을 받는다")
    void 본문이_비어_있으면_400을_받는다() throws Exception {
        long mapId = createMap();

        mockMvc.perform(post("/api/v1/maps/{mapId}/posts", mapId)
                .header(USER_HEADER, OWNER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(postBody("   "))))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("사진이 6장이면 400을 받는다")
    void 사진이_6장이면_400을_받는다() throws Exception {
        long mapId = createMap();
        Map<String, Object> body = postBody("사진 과다");
        body.put("imageUrls", List.of("a", "b", "c", "d", "e", "f"));

        mockMvc.perform(post("/api/v1/maps/{mapId}/posts", mapId)
                .header(USER_HEADER, OWNER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("같은 장소를 두 번 태그하면 400을 받는다")
    void 같은_장소를_두_번_태그하면_400을_받는다() throws Exception {
        long mapId = createMap();
        Map<String, Object> body = postBody("장소 중복");
        body.put("placeTags", List.of(
            Map.of("placeId", 5, "name", "블루보틀 성수점"),
            Map.of("placeId", 5, "name", "블루보틀 성수점")));

        mockMvc.perform(post("/api/v1/maps/{mapId}/posts", mapId)
                .header(USER_HEADER, OWNER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("MAP_020"));
    }

    @Test
    @DisplayName("댓글을 달면 201, 목록에서 조회된다")
    void 댓글을_달면_201_목록에서_조회된다() throws Exception {
        long mapId = createMap();
        long postId = createPost(mapId, "성수 카페 다녀왔어요");

        mockMvc.perform(post("/api/v1/maps/{mapId}/posts/{postId}/comments", mapId, postId)
                .header(USER_HEADER, OWNER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("content", "저도 가봤어요"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.content").value("저도 가봤어요"));

        mockMvc.perform(get("/api/v1/maps/{mapId}/posts/{postId}/comments", mapId, postId)
                .header(USER_HEADER, OWNER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content", hasSize(1)));
    }

    @Test
    @DisplayName("게시물을 삭제하면 목록에서 빠지고 댓글도 조회되지 않는다")
    void 게시물을_삭제하면_목록에서_빠지고_댓글도_조회되지_않는다() throws Exception {
        long mapId = createMap();
        long postId = createPost(mapId, "지울 게시물");
        mockMvc.perform(post("/api/v1/maps/{mapId}/posts/{postId}/comments", mapId, postId)
                .header(USER_HEADER, OWNER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("content", "댓글"))))
            .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/maps/{mapId}/posts/{postId}", mapId, postId)
                .header(USER_HEADER, OWNER))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/maps/{mapId}/posts", mapId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content", hasSize(0)));
        mockMvc.perform(get("/api/v1/maps/{mapId}/posts/{postId}/comments", mapId, postId)
                .header(USER_HEADER, OWNER))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("MAP_018"));
    }

    @Test
    @DisplayName("없는 지도의 로그탭을 조회하면 404를 받는다")
    void 없는_지도의_로그탭을_조회하면_404를_받는다() throws Exception {
        mockMvc.perform(get("/api/v1/maps/{mapId}/posts", 999999L).header(USER_HEADER, OWNER))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("MAP_001"));
    }

    private Map<String, Object> postBody(String content) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("content", content);
        body.put("imageUrls", List.of("https://cdn.moamap.com/map-posts/1/a.jpg"));
        body.put("placeTags", List.of(Map.of("placeId", 5, "name", "블루보틀 성수점")));
        return body;
    }

    private long createPost(long mapId, String content) throws Exception {
        String response = mockMvc.perform(post("/api/v1/maps/{mapId}/posts", mapId)
                .header(USER_HEADER, OWNER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(postBody(content))))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(response).path("data").path("id").asLong();
    }

    private long createMap() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "성수 카페 지도");
        body.put("visibility", "PUBLIC");
        body.put("tags", List.of());

        String response = mockMvc.perform(post("/api/v1/maps")
                .header(USER_HEADER, OWNER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(response).path("data").path("id").asLong();
    }

    private byte[] json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
    }
}
