package com.moamap.map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moamap.map.entity.MapEntity;
import com.moamap.map.entity.MapType;
import com.moamap.map.repository.MapEntityRepository;
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

    @Autowired
    private MapEntityRepository mapEntityRepository;

    @Test
    @DisplayName("공개 지도를 생성하면 생성자가 OWNER로 참여되고 memberCount는 1이다")
    void createCommunityMap() throws Exception {
        mockMvc.perform(post("/api/v1/maps")
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
        mockMvc.perform(post("/api/v1/maps")
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

        mockMvc.perform(post("/api/v1/maps/{mapId}/join", mapId).header(USER_HEADER, OTHER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.memberCount").value(2))
            .andExpect(jsonPath("$.data.joined").value(true))
            .andExpect(jsonPath("$.data.myRole").value("MEMBER"));
    }

    @Test
    @DisplayName("같은 지도에 두 번 참여하면 409로 거절된다")
    void joinTwiceConflict() throws Exception {
        long mapId = createMap(OWNER, "산책 지도", "PUBLIC");
        mockMvc.perform(post("/api/v1/maps/{mapId}/join", mapId).header(USER_HEADER, OTHER))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/maps/{mapId}/join", mapId).header(USER_HEADER, OTHER))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("MAP_005"));
    }

    @Test
    @DisplayName("초대 코드로 프라이빗 지도에 합류할 수 있다")
    void joinPrivateByInviteCode() throws Exception {
        String createResponse = mockMvc.perform(post("/api/v1/maps")
                .header(USER_HEADER, OWNER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("비밀 지도", "PRIVATE", List.of())))
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String inviteCode = objectMapper.readTree(createResponse).path("data").path("inviteCode").asText();

        String body = objectMapper.writeValueAsString(Map.of("inviteCode", inviteCode));
        mockMvc.perform(post("/api/v1/maps/join")
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
        mockMvc.perform(post("/api/v1/maps/join")
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
        mockMvc.perform(post("/api/v1/maps/{mapId}/join", mapId).header(USER_HEADER, OTHER)).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/maps").header(USER_HEADER, OTHER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0].type").value("COMMUNITY"))
            .andExpect(jsonPath("$.data.content[0].joined").value(true));
    }

    @Test
    @DisplayName("공식 지도 목록은 OFFICIAL 타입만 반환하고, 인증 헤더 없이도 조회된다")
    void officialMapListReturnsOfficialTypeOnly() throws Exception {
        createMap(OWNER, "커뮤니티 지도", "PUBLIC");
        mapEntityRepository.save(
            MapEntity.create("화장실 위치", "공공데이터 기반 공중화장실 위치", null, MapType.OFFICIAL, OWNER, null, null)
        );

        mockMvc.perform(get("/api/v1/maps/official"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content.length()").value(1))
            .andExpect(jsonPath("$.data.content[0].type").value("OFFICIAL"))
            .andExpect(jsonPath("$.data.content[0].name").value("화장실 위치"));
    }

    @Test
    @DisplayName("공식 지도도 커뮤니티와 동일한 join 엔드포인트로 참여할 수 있다")
    void joinOfficialMap() throws Exception {
        MapEntity official = mapEntityRepository.save(
            MapEntity.create("화장실 위치", "공공데이터 기반 공중화장실 위치", null, MapType.OFFICIAL, OWNER, null, null)
        );

        mockMvc.perform(post("/api/v1/maps/{mapId}/join", official.getId()).header(USER_HEADER, OTHER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.joined").value(true))
            .andExpect(jsonPath("$.data.myRole").value("MEMBER"));
    }

    @Test
    @DisplayName("멤버 역할 조회: 참여자는 역할을, 미참여자는 NONE을 반환한다")
    void memberRoleLookup() throws Exception {
        long mapId = createMap(OWNER, "역할 테스트 지도", "PUBLIC");

        mockMvc.perform(get("/api/v1/maps/{mapId}/members/{userId}", mapId, OWNER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.mapType").value("COMMUNITY"))
            .andExpect(jsonPath("$.data.role").value("OWNER"));

        mockMvc.perform(get("/api/v1/maps/{mapId}/members/{userId}", mapId, OTHER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.role").value("NONE"));
    }

    @Test
    @DisplayName("프라이빗 지도 상세는 멤버가 아니면 403이다")
    void privateDetailForbiddenForNonMember() throws Exception {
        long mapId = createMap(OWNER, "숨은 지도", "PRIVATE");

        mockMvc.perform(get("/api/v1/maps/{mapId}", mapId).header(USER_HEADER, OTHER))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("MAP_002"));
    }

    @Test
    @DisplayName("소유자가 아닌 사용자는 지도를 수정할 수 없다")
    void updateForbiddenForNonOwner() throws Exception {
        long mapId = createMap(OWNER, "수정 테스트", "PUBLIC");

        mockMvc.perform(patch("/api/v1/maps/{mapId}", mapId)
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

        mockMvc.perform(delete("/api/v1/maps/{mapId}", mapId).header(USER_HEADER, OTHER))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("MAP_004"));
    }

    @Test
    @DisplayName("이름이 비어 있으면 400으로 검증 실패한다")
    void createWithBlankNameBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/maps")
                .header(USER_HEADER, OWNER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("", "PUBLIC", List.of())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("COMMON_001"));
    }

    @Test
    @DisplayName("지도 상세 응답에 placeCount가 노출된다")
    void mapDetailIncludesPlaceCount() throws Exception {
        long mapId = createMap(OWNER, "장소 개수 상세 테스트", "PUBLIC");

        mockMvc.perform(get("/api/v1/maps/{mapId}", mapId).header(USER_HEADER, OWNER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.placeCount").value(0));
    }

    @Test
    @DisplayName("지도 목록 응답에 placeCount가 노출된다")
    void mapListIncludesPlaceCount() throws Exception {
        createMap(OWNER, "장소 개수 목록 테스트", "PUBLIC");

        mockMvc.perform(get("/api/v1/maps").header(USER_HEADER, OWNER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0].placeCount").value(0));
    }

    @Test
    @DisplayName("인증 헤더(X-User-Id)가 없으면 401이다")
    void createWithoutUserHeaderUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/maps")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("헤더없음", "PUBLIC", List.of())))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("COMMON_005"));
    }

    @Test
    @DisplayName("OWNER가 MEMBER를 ADMIN으로 승격하면 200이고, 이후 역할 조회에도 ADMIN이 반영된다")
    void ownerPromotesMemberToAdmin() throws Exception {
        long mapId = createMap(OWNER, "역할 변경 지도", "PUBLIC");
        mockMvc.perform(post("/api/v1/maps/{mapId}/join", mapId).header(USER_HEADER, OTHER))
            .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/maps/{mapId}/members/{userId}/role", mapId, OTHER)
                .header(USER_HEADER, OWNER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(roleBody("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.role").value("ADMIN"));

        mockMvc.perform(get("/api/v1/maps/{mapId}/members/{userId}", mapId, OTHER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.role").value("ADMIN"));
    }

    @Test
    @DisplayName("같은 role을 두 번 요청해도 멱등하게 둘 다 200이다")
    void changeRoleIsIdempotent() throws Exception {
        long mapId = createMap(OWNER, "멱등 테스트 지도", "PUBLIC");
        mockMvc.perform(post("/api/v1/maps/{mapId}/join", mapId).header(USER_HEADER, OTHER))
            .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/maps/{mapId}/members/{userId}/role", mapId, OTHER)
                .header(USER_HEADER, OWNER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(roleBody("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.role").value("ADMIN"));

        mockMvc.perform(put("/api/v1/maps/{mapId}/members/{userId}/role", mapId, OTHER)
                .header(USER_HEADER, OWNER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(roleBody("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.role").value("ADMIN"));
    }

    @Test
    @DisplayName("OWNER 자신을 대상으로 역할 변경을 요청하면 400 CANNOT_CHANGE_OWNER_ROLE이다")
    void cannotChangeOwnerRole() throws Exception {
        long mapId = createMap(OWNER, "소유자 보호 지도", "PUBLIC");

        mockMvc.perform(put("/api/v1/maps/{mapId}/members/{userId}/role", mapId, OWNER)
                .header(USER_HEADER, OWNER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(roleBody("MEMBER")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("MAP_014"));
    }

    @Test
    @DisplayName("멤버가 아닌 사용자를 대상으로 역할 변경을 요청하면 404 TARGET_NOT_MAP_MEMBER이다")
    void targetNotMapMember() throws Exception {
        long mapId = createMap(OWNER, "비멤버 대상 지도", "PUBLIC");

        mockMvc.perform(put("/api/v1/maps/{mapId}/members/{userId}/role", mapId, OTHER)
                .header(USER_HEADER, OWNER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(roleBody("ADMIN")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("MAP_013"));
    }

    @Test
    @DisplayName("OWNER가 아닌 요청자가 역할 변경을 시도하면 403 NOT_MAP_OWNER이다")
    void nonOwnerCannotChangeRole() throws Exception {
        long mapId = createMap(OWNER, "권한 없는 요청자 지도", "PUBLIC");
        long thirdUser = 3L;
        mockMvc.perform(post("/api/v1/maps/{mapId}/join", mapId).header(USER_HEADER, OTHER))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/maps/{mapId}/join", mapId).header(USER_HEADER, thirdUser))
            .andExpect(status().isOk());

        // MEMBER가 다른 MEMBER의 역할 변경을 시도
        mockMvc.perform(put("/api/v1/maps/{mapId}/members/{userId}/role", mapId, thirdUser)
                .header(USER_HEADER, OTHER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(roleBody("ADMIN")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("MAP_004"));
    }

    @Test
    @DisplayName("승격된 ADMIN도 역할 변경을 시도하면 403 NOT_MAP_OWNER이다")
    void adminCannotChangeRole() throws Exception {
        long mapId = createMap(OWNER, "관리자 요청자 지도", "PUBLIC");
        long thirdUser = 3L;
        mockMvc.perform(post("/api/v1/maps/{mapId}/join", mapId).header(USER_HEADER, OTHER))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/maps/{mapId}/join", mapId).header(USER_HEADER, thirdUser))
            .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/maps/{mapId}/members/{userId}/role", mapId, OTHER)
                .header(USER_HEADER, OWNER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(roleBody("ADMIN")))
            .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/maps/{mapId}/members/{userId}/role", mapId, thirdUser)
                .header(USER_HEADER, OTHER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(roleBody("ADMIN")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("MAP_004"));
    }

    @Test
    @DisplayName("role에 OWNER를 요청하면 400 INVALID_ROLE_ASSIGNMENT이다")
    void invalidRoleAssignmentOwner() throws Exception {
        long mapId = createMap(OWNER, "잘못된 역할 지도1", "PUBLIC");
        mockMvc.perform(post("/api/v1/maps/{mapId}/join", mapId).header(USER_HEADER, OTHER))
            .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/maps/{mapId}/members/{userId}/role", mapId, OTHER)
                .header(USER_HEADER, OWNER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(roleBody("OWNER")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("MAP_015"));
    }

    @Test
    @DisplayName("role에 NONE을 요청하면 400 INVALID_ROLE_ASSIGNMENT이다")
    void invalidRoleAssignmentNone() throws Exception {
        long mapId = createMap(OWNER, "잘못된 역할 지도2", "PUBLIC");
        mockMvc.perform(post("/api/v1/maps/{mapId}/join", mapId).header(USER_HEADER, OTHER))
            .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/maps/{mapId}/members/{userId}/role", mapId, OTHER)
                .header(USER_HEADER, OWNER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(roleBody("NONE")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("MAP_015"));
    }

    private String roleBody(String role) throws Exception {
        return objectMapper.writeValueAsString(Map.of("role", role));
    }

    private long createMap(long userId, String name, String visibility) throws Exception {
        String response = mockMvc.perform(post("/api/v1/maps")
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
