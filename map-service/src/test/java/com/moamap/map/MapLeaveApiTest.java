package com.moamap.map;

import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
 * 지도 나가기 API의 계약(상태 코드·응답 모양)을 검증한다. 위임 규칙은 MapLeaveTest가 다룬다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MapLeaveApiTest {

    private static final String USER_HEADER = "X-User-Id";
    private static final long OWNER = 1L;
    private static final long MEMBER = 2L;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private UserClient userClient;
    @MockitoBean private PlaceClient placeClient;

    @Test
    @DisplayName("방장이 나가면 새 방장을 응답에 담는다")
    void ownerLeaveReturnsNewOwner() throws Exception {
        given(userClient.findProfiles(anyCollection()))
            .willReturn(Map.of(MEMBER, new UserProfileResponse(MEMBER, "새 방장", null)));
        long mapId = createMap();
        join(mapId, MEMBER);

        mockMvc.perform(delete("/api/v1/maps/{mapId}/members/me", mapId).header(USER_HEADER, OWNER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.newOwner.userId").value(MEMBER))
            .andExpect(jsonPath("$.data.newOwner.nickname").value("새 방장"));
    }

    @Test
    @DisplayName("멤버가 나가면 새 방장이 비어 있다")
    void memberLeaveHasNoNewOwner() throws Exception {
        long mapId = createMap();
        join(mapId, MEMBER);

        mockMvc.perform(delete("/api/v1/maps/{mapId}/members/me", mapId).header(USER_HEADER, MEMBER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.newOwner").doesNotExist());
    }

    @Test
    @DisplayName("혼자 남은 방장이 나가려 하면 400(MAP_008)이다")
    void soleOwnerCannotLeave() throws Exception {
        long mapId = createMap();

        mockMvc.perform(delete("/api/v1/maps/{mapId}/members/me", mapId).header(USER_HEADER, OWNER))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("MAP_008"));
    }

    private void join(long mapId, long userId) throws Exception {
        mockMvc.perform(post("/api/v1/maps/{mapId}/join", mapId).header(USER_HEADER, userId))
            .andExpect(status().is2xxSuccessful());
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
