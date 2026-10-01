package com.moamap.map.repository;

import java.util.List;
import com.moamap.map.config.JpaAuditingConfig;
import com.moamap.map.entity.MapEntity;
import com.moamap.map.entity.MapMember;
import com.moamap.map.entity.MapRole;
import com.moamap.map.entity.MapType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 탐색 탭 목록에서 요청자가 참여 중인 지도를 제외하는 쿼리를 검증한다.
 * 페이지 크기와 전체 개수가 제외 후 기준이어야 하므로 쿼리 자체를 실행해본다.
 */
@DataJpaTest
@Import(JpaAuditingConfig.class)
class MapExploreQueryTest {

    private static final Long USER_ID = 10L;
    private static final Long OTHER_USER_ID = 20L;

    @Autowired
    private MapEntityRepository mapRepository;

    @Autowired
    private MapMemberRepository mapMemberRepository;

    private MapEntity joinedFoodMap;

    @BeforeEach
    void setUp() {
        joinedFoodMap = mapRepository.save(map("참여한 맛집 지도", MapType.COMMUNITY, List.of("맛집")));
        mapRepository.save(map("미참여 맛집 지도", MapType.COMMUNITY, List.of("맛집")));
        mapRepository.save(map("미참여 캠핑 지도", MapType.COMMUNITY, List.of("캠핑")));
        MapEntity privateMap = mapRepository.save(map("비공개 지도", MapType.PRIVATE, List.of("맛집")));

        mapMemberRepository.save(MapMember.of(joinedFoodMap.getId(), USER_ID, MapRole.MEMBER));
        mapMemberRepository.save(MapMember.of(privateMap.getId(), USER_ID, MapRole.OWNER));
        // 다른 사용자의 참여는 제외 조건에 영향을 주지 않아야 한다.
        mapMemberRepository.save(MapMember.of(joinedFoodMap.getId(), OTHER_USER_ID, MapRole.OWNER));
    }

    @Test
    void 참여_중인_지도를_제외한_같은_타입의_지도만_조회한다() {
        Page<MapEntity> page = mapRepository.findNotJoinedByType(USER_ID, MapType.COMMUNITY, PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(MapEntity::getName)
            .containsExactlyInAnyOrder("미참여 맛집 지도", "미참여 캠핑 지도");
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    void 태그_필터와_함께_참여_중인_지도를_제외한다() {
        Page<MapEntity> page = mapRepository.findNotJoinedByTypeAndTag(
            USER_ID, MapType.COMMUNITY, "맛집", PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(MapEntity::getName)
            .containsExactly("미참여 맛집 지도");
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    void 참여한_지도가_없는_사용자에게는_모든_지도를_보여준다() {
        Page<MapEntity> page = mapRepository.findNotJoinedByType(999L, MapType.COMMUNITY, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    private static MapEntity map(String name, MapType type, List<String> tags) {
        String inviteCode = (type == MapType.PRIVATE) ? "PRIVATE1" : null;
        return MapEntity.create(name, "설명", null, type, OTHER_USER_ID, tags, inviteCode);
    }
}
