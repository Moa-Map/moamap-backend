package com.moamap.map;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moamap.map.place.PlaceClient;
import com.moamap.map.user.UserClient;
import com.moamap.map.user.dto.UserProfileResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 멤버 목록 API의 계약(상태 코드·응답 모양)을 검증한다. 정렬·권한 규칙은 MapMemberListTest가 다룬다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MapMemberListApiTest {

    private static final String USER_HEADER = "X-User-Id";
    private static final long OWNER = 1L;
    private static final long OUTSIDER = 99L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserClient userClient;

    // 목으로 바꾸지 않으면 실제 place-service로 붙으려다 실패하고 폴백을 타, 필드가 늘 null이 된다.
    @MockitoBean
    private PlaceClient placeClient;

    @Test
    @DisplayName("멤버 목록과 참여자 수를 함께 반환한다")
    void returnsMembers() throws Exception {
        given(userClient.findProfiles(anyCollection()))
            .willReturn(Map.of(OWNER, new UserProfileResponse(OWNER, "방장님", "https://img/o.jpg")));
        given(placeClient.countByCreator(anyLong(), anyCollection())).willReturn(Map.of(OWNER, 7L));
        long mapId = createMap();

        mockMvc.perform(get("/api/v1/maps/{mapId}/members", mapId).header(USER_HEADER, OWNER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.memberCount").value(1))
            .andExpect(jsonPath("$.data.members", hasSize(1)))
            .andExpect(jsonPath("$.data.members[0].userId").value(OWNER))
            .andExpect(jsonPath("$.data.members[0].nickname").value("방장님"))
            .andExpect(jsonPath("$.data.members[0].profileImageUrl").value("https://img/o.jpg"))
            .andExpect(jsonPath("$.data.members[0].role").value("OWNER"))
            .andExpect(jsonPath("$.data.members[0].placeCount").value(7));
    }

    @Test
    @DisplayName("참여하지 않은 사용자는 403을 받는다")
    void rejectsNonMember() throws Exception {
        long mapId = createMap();

        mockMvc.perform(get("/api/v1/maps/{mapId}/members", mapId).header(USER_HEADER, OUTSIDER))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("MAP_002"));
    }

    @Test
    @DisplayName("로그인하지 않으면 401을 받는다")
    void rejectsAnonymous() throws Exception {
        long mapId = createMap();

        mockMvc.perform(get("/api/v1/maps/{mapId}/members", mapId))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("없는 지도를 조회하면 404를 받는다")
    void rejectsUnknownMap() throws Exception {
        mockMvc.perform(get("/api/v1/maps/{mapId}/members", 999999L).header(USER_HEADER, OWNER))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("MAP_001"));
    }

    private long createMap() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "여행 지도");
        body.put("visibility", "PUBLIC");
        body.put("tags", List.of());

        String response = mockMvc.perform(post("/api/v1/maps")
                .header(USER_HEADER, OWNER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body).getBytes(StandardCharsets.UTF_8)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(response).path("data").path("id").asLong();
    }
}
