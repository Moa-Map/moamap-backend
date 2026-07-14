package com.moamap.map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MapApiIntegrationTest {

    private static final String USER_HEADER = "X-User-Id";
    private static final long OWNER = 1L;
    private static final long OTHER = 2L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("공개 지도를 생성하면 생성자가 OWNER로 참여되고 memberCount는 1이다")
    void createCommunityMap() throws Exception {
        mockMvc.perform(post("/maps")
                .header(USER_HEADER, OWNER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("서울 팝업스토어 맵", "PUBLIC", List.of("맛집", "데이트"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.type").value("COMMUNITY"))
            .andExpect(jsonPath("$.data.ownerId").value(OWNER))
            .andExpect(jsonPath("$.data.memberCount").value(1))
            .andExpect(jsonPath("$.data.myRole").value("OWNER"))
            .andExpect(jsonPath("$.data.inviteCode").doesNotExist());
    }

    @Test
    @DisplayName("프라이빗 지도를 생성하면 소유자에게 초대 코드가 발급된다")
    void createPrivateMapGeneratesInviteCode() throws Exception {
        mockMvc.perform(post("/maps")
                .header(USER_HEADER, OWNER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("우리끼리 지도", "PRIVATE", List.of())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.type").value("PRIVATE"))
            .andExpect(jsonPath("$.data.inviteCode").isNotEmpty());
    }

    @Test
    @DisplayName("공개 지도에 다른 사용자가 참여하면 memberCount가 증가한다")
    void joinCommunityMap() throws Exception {
        long mapId = createMap(OWNER, "카페 지도", "PUBLIC");

        mockMvc.perform(post("/maps/{mapId}/join", mapId).header(USER_HEADER, OTHER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.memberCount").value(2))
            .andExpect(jsonPath("$.data.joined").value(true))
            .andExpect(jsonPath("$.data.myRole").value("MEMBER"));
    }

    @Test
    @DisplayName("같은 지도에 두 번 참여하면 409로 거절된다")
    void joinTwiceConflict() throws Exception {
        long mapId = createMap(OWNER, "산책 지도", "PUBLIC");
        mockMvc.perform(post("/maps/{mapId}/join", mapId).header(USER_HEADER, OTHER))
            .andExpect(status().isOk());

        mockMvc.perform(post("/maps/{mapId}/join", mapId).header(USER_HEADER, OTHER))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("MAP_005"));
    }

    @Test
    @DisplayName("초대 코드로 프라이빗 지도에 합류할 수 있다")
    void joinPrivateByInviteCode() throws Exception {
        String createResponse = mockMvc.perform(post("/maps")
                .header(USER_HEADER, OWNER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("비밀 지도", "PRIVATE", List.of())))
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String inviteCode = objectMapper.readTree(createResponse).path("data").path("inviteCode").asText();

        String body = objectMapper.writeValueAsString(Map.of("inviteCode", inviteCode));
        mockMvc.perform(post("/maps/join")
                .header(USER_HEADER, OTHER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.joined").value(true));
    }

    @Test
    @DisplayName("잘못된 초대 코드는 404로 응답한다")
    void joinWithInvalidInviteCode() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("inviteCode", "ZZZZZZ"));
        mockMvc.perform(post("/maps/join")
                .header(USER_HEADER, OTHER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("MAP_007"));
    }

    @Test
    @DisplayName("커뮤니티 목록은 공개 지도를 반환하고 요청자의 참여 여부를 표시한다")
    void communityListShowsJoinedFlag() throws Exception {
        long mapId = createMap(OWNER, "힙플 지도", "PUBLIC");
        mockMvc.perform(post("/maps/{mapId}/join", mapId).header(USER_HEADER, OTHER)).andExpect(status().isOk());

        mockMvc.perform(get("/maps").header(USER_HEADER, OTHER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0].type").value("COMMUNITY"))
            .andExpect(jsonPath("$.data.content[0].joined").value(true));
    }

    @Test
    @DisplayName("멤버 역할 조회: 참여자는 역할을, 미참여자는 NONE을 반환한다")
    void memberRoleLookup() throws Exception {
        long mapId = createMap(OWNER, "역할 테스트 지도", "PUBLIC");

        mockMvc.perform(get("/maps/{mapId}/members/{userId}", mapId, OWNER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.mapType").value("COMMUNITY"))
            .andExpect(jsonPath("$.data.role").value("OWNER"));

        mockMvc.perform(get("/maps/{mapId}/members/{userId}", mapId, OTHER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.role").value("NONE"));
    }

    @Test
    @DisplayName("프라이빗 지도 상세는 멤버가 아니면 403이다")
    void privateDetailForbiddenForNonMember() throws Exception {
        long mapId = createMap(OWNER, "숨은 지도", "PRIVATE");

        mockMvc.perform(get("/maps/{mapId}", mapId).header(USER_HEADER, OTHER))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("MAP_002"));
    }

    @Test
    @DisplayName("소유자가 아닌 사용자는 지도를 수정할 수 없다")
    void updateForbiddenForNonOwner() throws Exception {
        long mapId = createMap(OWNER, "수정 테스트", "PUBLIC");

        mockMvc.perform(patch("/maps/{mapId}", mapId)
                .header(USER_HEADER, OTHER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "해킹시도"))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("MAP_003"));
    }

    @Test
    @DisplayName("소유자가 아닌 사용자는 지도를 삭제할 수 없다")
    void deleteForbiddenForNonOwner() throws Exception {
        long mapId = createMap(OWNER, "삭제 테스트", "PUBLIC");

        mockMvc.perform(delete("/maps/{mapId}", mapId).header(USER_HEADER, OTHER))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("MAP_004"));
    }

    @Test
    @DisplayName("이름이 비어 있으면 400으로 검증 실패한다")
    void createWithBlankNameBadRequest() throws Exception {
        mockMvc.perform(post("/maps")
                .header(USER_HEADER, OWNER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("", "PUBLIC", List.of())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("COMMON_001"));
    }

    @Test
    @DisplayName("인증 헤더(X-User-Id)가 없으면 401이다")
    void createWithoutUserHeaderUnauthorized() throws Exception {
        mockMvc.perform(post("/maps")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("헤더없음", "PUBLIC", List.of())))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("COMMON_005"));
    }

    private long createMap(long userId, String name, String visibility) throws Exception {
        String response = mockMvc.perform(post("/maps")
                .header(USER_HEADER, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody(name, visibility, List.of())))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(response).path("data").path("id").asLong();
    }

    private String createBody(String name, String visibility, List<String> tags) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("description", "테스트 지도 설명");
        body.put("visibility", visibility);
        body.put("tags", tags);
        return objectMapper.writeValueAsString(body);
    }
}
