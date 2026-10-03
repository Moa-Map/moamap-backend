package com.moamap.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moamap.map.entity.MapEntity;
import com.moamap.map.entity.MapMember;
import com.moamap.map.entity.MapRole;
import com.moamap.map.entity.MapType;
import com.moamap.map.repository.MapEntityRepository;
import com.moamap.map.repository.MapMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 모음 탭 순서 변경 API.
 *
 * 순서 저장과 조회가 한 쌍으로 맞물려야 화면이 흔들리지 않으므로, 변경 후 목록 조회까지 한 번에 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MapOrderApiTest {

    private static final String USER_HEADER = "X-User-Id";
    private static final long ME = 100L;
    private static final long STRANGER = 200L;
    private static final String ORDER_URL = "/api/v1/maps/me/order";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MapEntityRepository mapRepository;

    @Autowired
    private MapMemberRepository mapMemberRepository;

    private Long alpha;
    private Long beta;
    private Long gamma;
    private int inviteCodeSeq;

    @BeforeEach
    void setUp() {
        alpha = joinPrivateMap("알파", ME);
        beta = joinPrivateMap("베타", ME);
        gamma = joinPrivateMap("감마", ME);
    }

    @Test
    @DisplayName("보낸 순서대로 모음 탭 목록이 바뀐다")
    void reorder() throws Exception {
        mockMvc.perform(patch(ORDER_URL)
                .header(USER_HEADER, ME)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(MapType.PRIVATE, List.of(beta, gamma, alpha))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        assertMyMapNames("베타", "감마", "알파");
    }

    @Test
    @DisplayName("같은 요청을 두 번 보내도 결과가 같다")
    void idempotent() throws Exception {
        String request = body(MapType.PRIVATE, List.of(gamma, alpha, beta));

        reorderExpecting(request, status().isOk());
        reorderExpecting(request, status().isOk());

        assertMyMapNames("감마", "알파", "베타");
    }

    @Test
    @DisplayName("일부만 보내면 거부하고 순서를 건드리지 않는다")
    void rejectPartialList() throws Exception {
        mockMvc.perform(patch(ORDER_URL)
                .header(USER_HEADER, ME)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(MapType.PRIVATE, List.of(beta, alpha))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("MAP_022"));

        // 순서를 지정한 적 없는 상태 그대로여야 한다(최신순).
        assertMyMapNames("감마", "베타", "알파");
    }

    @Test
    @DisplayName("같은 지도를 두 번 보내면 거부한다")
    void rejectDuplicates() throws Exception {
        mockMvc.perform(patch(ORDER_URL)
                .header(USER_HEADER, ME)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(MapType.PRIVATE, List.of(alpha, alpha, beta))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("MAP_022"));
    }

    @Test
    @DisplayName("참여하지 않은 지도를 섞어 보내면 거부한다")
    void rejectForeignMap() throws Exception {
        Long strangersMap = joinPrivateMap("남의 지도", STRANGER);

        mockMvc.perform(patch(ORDER_URL)
                .header(USER_HEADER, ME)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(MapType.PRIVATE, List.of(alpha, beta, gamma, strangersMap))))
            .andExpect(status().isBadRequest())
            // 남의 지도가 존재하는지 여부는 응답에서 알 수 없어야 한다 — 누락·중복과 같은 코드로 막힌다.
            .andExpect(jsonPath("$.error.code").value("MAP_022"));

        assertThat(mapMemberRepository.findByMapIdAndUserId(strangersMap, ME)).isEmpty();
    }

    @Test
    @DisplayName("다른 탭의 지도를 섞어 보내면 거부한다")
    void rejectOtherTab() throws Exception {
        Long communityMap = joinCommunityMap("커뮤니티 지도", ME);

        mockMvc.perform(patch(ORDER_URL)
                .header(USER_HEADER, ME)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(MapType.PRIVATE, List.of(alpha, beta, gamma, communityMap))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("MAP_022"));
    }

    @Test
    @DisplayName("내가 바꾼 순서는 다른 사용자의 목록에 영향을 주지 않는다")
    void orderIsPerUser() throws Exception {
        mapMemberRepository.save(MapMember.of(alpha, STRANGER, MapRole.MEMBER));
        mapMemberRepository.save(MapMember.of(beta, STRANGER, MapRole.MEMBER));

        mockMvc.perform(patch(ORDER_URL)
                .header(USER_HEADER, ME)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(MapType.PRIVATE, List.of(beta, gamma, alpha))))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/maps/me").param("type", "PRIVATE").header(USER_HEADER, STRANGER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0].name").value("베타"))
            .andExpect(jsonPath("$.data.content[1].name").value("알파"));
    }

    @Test
    @DisplayName("빈 목록은 거부한다")
    void rejectEmptyList() throws Exception {
        mockMvc.perform(patch(ORDER_URL)
                .header(USER_HEADER, ME)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(MapType.PRIVATE, List.of())))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("목록에 null이 섞여 있어도 500으로 새지 않는다")
    void rejectNullElement() throws Exception {
        String request = """
            {"type":"PRIVATE","mapIds":[%d,null,%d]}""".formatted(alpha, beta);

        mockMvc.perform(patch(ORDER_URL)
                .header(USER_HEADER, ME)
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("로그인하지 않으면 순서를 바꿀 수 없다")
    void rejectWithoutUserHeader() throws Exception {
        // 게이트웨이가 JWT를 검증해 X-User-Id를 넣어준다. 헤더가 없다는 건 인증을 거치지 않았다는 뜻이다.
        mockMvc.perform(patch(ORDER_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(MapType.PRIVATE, List.of(alpha, beta, gamma))))
            .andExpect(status().isUnauthorized());
    }

    private void reorderExpecting(String request,
            org.springframework.test.web.servlet.ResultMatcher expected) throws Exception {
        mockMvc.perform(patch(ORDER_URL)
                .header(USER_HEADER, ME)
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
            .andExpect(expected);
    }

    private void assertMyMapNames(String... names) throws Exception {
        var result = mockMvc.perform(get("/api/v1/maps/me").param("type", "PRIVATE").header(USER_HEADER, ME))
            .andExpect(status().isOk());
        for (int i = 0; i < names.length; i++) {
            result.andExpect(jsonPath("$.data.content[%d].name".formatted(i)).value(names[i]));
        }
        result.andExpect(jsonPath("$.data.totalElements").value(names.length));
    }

    private String body(MapType type, List<Long> mapIds) throws Exception {
        return objectMapper.writeValueAsString(java.util.Map.of("type", type.name(), "mapIds", mapIds));
    }

    private Long joinPrivateMap(String name, Long userId) {
        return saveAndJoin(name, MapType.PRIVATE, userId);
    }

    private Long joinCommunityMap(String name, Long userId) {
        return saveAndJoin(name, MapType.COMMUNITY, userId);
    }

    private Long saveAndJoin(String name, MapType type, Long userId) {
        String inviteCode = (type == MapType.PRIVATE) ? "CODE%06d".formatted(++inviteCodeSeq) : null;
        MapEntity map = mapRepository.save(
            MapEntity.create(name, "설명", null, type, userId, List.of(), inviteCode));
        mapMemberRepository.save(MapMember.of(map.getId(), userId, MapRole.OWNER));
        return map.getId();
    }
}
