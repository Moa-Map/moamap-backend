package com.moamap.map.repository;

import java.util.List;
import com.moamap.map.config.JpaAuditingConfig;
import com.moamap.map.entity.MapEntity;
import com.moamap.map.entity.MapType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 추천 후보를 뽑는 쿼리가 실제 DB에서 의도대로 동작하는지 검증한다.
 * JPQL은 컴파일 시점에 검증되지 않으므로 쿼리 자체를 실행해봐야 한다.
 *
 * createdAt은 Auditing으로 채워지는데 @DataJpaTest는 설정 클래스를 자동으로 올리지 않아 직접 import한다.
 */
@DataJpaTest
@Import(JpaAuditingConfig.class)
class MapRecommendationQueryTest {

    @Autowired
    private MapEntityRepository mapRepository;

    @BeforeEach
    void setUp() {
        mapRepository.save(map("맛집 지도", MapType.COMMUNITY, 1L, List.of("맛집", "카페"), 100));
        mapRepository.save(map("데이트 지도", MapType.COMMUNITY, 2L, List.of("맛집", "데이트"), 50));
        mapRepository.save(map("캠핑 지도", MapType.COMMUNITY, 3L, List.of("캠핑"), 10));
        mapRepository.save(map("비공개 지도", MapType.PRIVATE, 4L, List.of("맛집"), 5));
    }

    @Test
    void 관심_태그와_겹치는_커뮤니티_지도만_후보로_뽑는다() {
        List<Long> ids = mapRepository.findCandidateIdsByTags(
            MapType.COMMUNITY, List.of("맛집"), PageRequest.of(0, 10));

        List<MapEntity> found = mapRepository.findAllWithTagsByIdIn(ids);
        assertThat(found).extracting(MapEntity::getName)
            .containsExactlyInAnyOrder("맛집 지도", "데이트 지도");
    }

    @Test
    void 겹치는_태그가_많은_지도가_앞에_온다() {
        List<Long> ids = mapRepository.findCandidateIdsByTags(
            MapType.COMMUNITY, List.of("맛집", "카페"), PageRequest.of(0, 10));

        MapEntity first = mapRepository.findAllWithTagsByIdIn(List.of(ids.get(0))).get(0);
        assertThat(first.getName()).isEqualTo("맛집 지도");
    }

    @Test
    void 후보_개수를_제한할_수_있다() {
        List<Long> ids = mapRepository.findCandidateIdsByTags(
            MapType.COMMUNITY, List.of("맛집"), PageRequest.of(0, 1));

        assertThat(ids).hasSize(1);
    }

    @Test
    void 인기_지도는_멤버가_많은_순으로_나온다() {
        List<Long> ids = mapRepository.findPopularIds(MapType.COMMUNITY, PageRequest.of(0, 10));

        List<MapEntity> found = mapRepository.findAllWithTagsByIdIn(ids);
        assertThat(found).extracting(MapEntity::getName)
            .doesNotContain("비공개 지도");
        assertThat(ids).hasSize(3);
    }

    @Test
    void 태그를_함께_읽어와_지연로딩_없이_점수를_계산할_수_있다() {
        List<Long> ids = mapRepository.findPopularIds(MapType.COMMUNITY, PageRequest.of(0, 10));

        List<MapEntity> found = mapRepository.findAllWithTagsByIdIn(ids);
        assertThat(found).allSatisfy(map -> assertThat(map.getTags()).isNotNull());
    }

    private MapEntity map(String name, MapType type, Long ownerId, List<String> tags, int memberCount) {
        MapEntity map = MapEntity.create(name, "설명", null, type, ownerId, tags, null);
        for (int i = 1; i < memberCount; i++) {
            map.increaseMemberCount();
        }
        return map;
    }
}
