package com.moamap.map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moamap.map.place.PlaceClient;
import com.moamap.map.user.UserAdminClient;
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

/**
 * 신고 API의 계약(상태 코드·응답 모양)을 검증한다. 신고 규칙은 MapReportServiceTest가 다룬다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MapReportApiTest {

    private static final String USER_HEADER = "X-User-Id";
    private static final long OWNER = 1L;
    private static final long OUTSIDER = 2L;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private UserClient userClient;
    @MockitoBean private PlaceClient placeClient;
    @MockitoBean private UserAdminClient userAdminClient;

    @Test
    @DisplayName("지도를 신고하면 201과 신고 ID를 준다")
    void reportMap() throws Exception {
        long mapId = createMap();

        mockMvc.perform(post("/api/v1/maps/{mapId}/reports", mapId).header(USER_HEADER, OUTSIDER)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"SEXUAL\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.reportId").isNumber());
    }

    @Test
    @DisplayName("기타 사유인데 내용이 없으면 400이다")
    void otherReasonNeedsDetail() throws Exception {
        long mapId = createMap();

        mockMvc.perform(post("/api/v1/maps/{mapId}/reports", mapId).header(USER_HEADER, OUTSIDER)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"OTHER\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("로그인하지 않으면 401이다")
    void requiresLogin() throws Exception {
        long mapId = createMap();

        mockMvc.perform(post("/api/v1/maps/{mapId}/reports", mapId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"SPAM\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("내 지도를 신고하면 400(MAP_025)이다")
    void cannotReportOwnMap() throws Exception {
        long mapId = createMap();

        mockMvc.perform(post("/api/v1/maps/{mapId}/reports", mapId).header(USER_HEADER, OWNER)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"SPAM\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("MAP_025"));
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
