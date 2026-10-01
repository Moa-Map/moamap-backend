package com.moamap.map.service;

import java.util.List;
import com.moamap.map.config.JpaAuditingConfig;
import com.moamap.map.dto.MapSort;
import com.moamap.map.dto.MapSummaryResponse;
import com.moamap.map.entity.MapEntity;
import com.moamap.map.entity.MapMember;
import com.moamap.map.entity.MapRole;
import com.moamap.map.entity.MapType;
import com.moamap.map.place.PlaceClient;
import com.moamap.map.repository.MapEntityRepository;
import com.moamap.map.repository.MapMemberRepository;
import com.moamap.map.user.UserClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 탐색 탭 커뮤니티 지도 목록은 로그인 사용자가 이미 참여 중인 지도를 빼고 보여준다.
 * 비로그인이면 참여 정보가 없으므로 전체를 보여준다.
 */
@DataJpaTest
@Import({JpaAuditingConfig.class, MapService.class, InviteCodeGenerator.class})
class CommunityMapExploreTest {

    private static final long USER_ID = 10L;
    private static final long OWNER_ID = 20L;

    @Autowired
    private MapService mapService;

    @Autowired
    private MapEntityRepository mapRepository;

    @Autowired
    private MapMemberRepository mapMemberRepository;

    @MockitoBean
    private UserClient userClient;

    @MockitoBean
    private PlaceClient placeClient;

    @BeforeEach
    void setUp() {
        MapEntity joined = mapRepository.save(map("참여한 맛집 지도", List.of("맛집")));
        mapRepository.save(map("미참여 맛집 지도", List.of("맛집")));
        mapRepository.save(map("미참여 캠핑 지도", List.of("캠핑")));
        mapMemberRepository.save(MapMember.of(joined.getId(), USER_ID, MapRole.MEMBER));
    }

    @Test
    void 로그인_사용자에게는_참여_중인_지도를_빼고_보여준다() {
        Page<MapSummaryResponse> page = mapService.getCommunityMaps(null, MapSort.LATEST, PageRequest.of(0, 20), USER_ID);

        assertThat(page.getContent()).extracting(MapSummaryResponse::name)
            .containsExactlyInAnyOrder("미참여 맛집 지도", "미참여 캠핑 지도");
        assertThat(page.getContent()).extracting(MapSummaryResponse::joined).containsOnly(false);
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    void 태그로_거를_때도_참여_중인_지도를_뺀다() {
        Page<MapSummaryResponse> page = mapService.getCommunityMaps("맛집", MapSort.POPULAR, PageRequest.of(0, 20), USER_ID);

        assertThat(page.getContent()).extracting(MapSummaryResponse::name)
            .containsExactly("미참여 맛집 지도");
    }

    @Test
    void 비로그인이면_전체_지도를_보여준다() {
        Page<MapSummaryResponse> page = mapService.getCommunityMaps(null, MapSort.LATEST, PageRequest.of(0, 20), null);

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).extracting(MapSummaryResponse::joined).containsOnly(false);
    }

    @Test
    void 비로그인이면_태그로_거른_전체_지도를_보여준다() {
        Page<MapSummaryResponse> page = mapService.getCommunityMaps("맛집", MapSort.LATEST, PageRequest.of(0, 20), null);

        assertThat(page.getContent()).extracting(MapSummaryResponse::name)
            .containsExactlyInAnyOrder("참여한 맛집 지도", "미참여 맛집 지도");
    }

    @Test
    void 참여_지도를_빼도_인기순은_참여_인원순을_유지한다() {
        // 저장 순서와 인원 순서를 일부러 어긋나게 둬서, 정렬이 실제로 적용되는지 본다.
        mapRepository.save(map("인원 30 지도", List.of("맛집"), 30));
        mapRepository.save(map("인원 50 지도", List.of("맛집"), 50));
        MapEntity joinedPopular = mapRepository.save(map("참여한 인원 100 지도", List.of("맛집"), 100));
        mapRepository.save(map("인원 40 지도", List.of("캠핑"), 40));
        mapMemberRepository.save(MapMember.of(joinedPopular.getId(), USER_ID, MapRole.MEMBER));

        // 4위부터는 인원이 1명으로 같아 생성 시각이 순서를 정하므로, 인원이 갈리는 앞 3개만 본다.
        Page<MapSummaryResponse> page = mapService.getCommunityMaps(null, MapSort.POPULAR, PageRequest.of(0, 3), USER_ID);

        assertThat(page.getContent()).extracting(MapSummaryResponse::name)
            .containsExactly("인원 50 지도", "인원 40 지도", "인원 30 지도");
        assertThat(page.getTotalElements()).isEqualTo(5);
    }

    @Test
    void 태그로_거를_때도_인기순은_참여_인원순을_유지한다() {
        mapRepository.save(map("인원 30 지도", List.of("맛집"), 30));
        mapRepository.save(map("인원 50 지도", List.of("맛집"), 50));
        MapEntity joinedPopular = mapRepository.save(map("참여한 인원 100 지도", List.of("맛집"), 100));
        mapRepository.save(map("인원 40 지도", List.of("캠핑"), 40));
        mapMemberRepository.save(MapMember.of(joinedPopular.getId(), USER_ID, MapRole.MEMBER));

        Page<MapSummaryResponse> page = mapService.getCommunityMaps("맛집", MapSort.POPULAR, PageRequest.of(0, 20), USER_ID);

        assertThat(page.getContent()).extracting(MapSummaryResponse::name)
            .containsExactly("인원 50 지도", "인원 30 지도", "미참여 맛집 지도");
    }

    private static MapEntity map(String name, List<String> tags) {
        return MapEntity.create(name, "설명", null, MapType.COMMUNITY, OWNER_ID, tags, null);
    }

    private static MapEntity map(String name, List<String> tags, int memberCount) {
        MapEntity map = map(name, tags);
        for (int i = 1; i < memberCount; i++) {
            map.increaseMemberCount();
        }
        return map;
    }
}
