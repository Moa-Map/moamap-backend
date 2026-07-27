package com.moamap.map.recommendation;

import java.time.LocalDateTime;
import java.util.List;
import com.moamap.map.entity.MapEntity;
import com.moamap.map.entity.MapType;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 추천 테스트용 지도 생성 도우미. id/memberCount/createdAt은 영속화 과정에서 채워지는 값이라
 * 테스트에서는 리플렉션으로 직접 넣는다.
 */
final class RecommendationFixtures {

    static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 1, 0, 0);

    private RecommendationFixtures() {
    }

    static MapEntity map(long id, long ownerId, List<String> tags, int memberCount, LocalDateTime createdAt) {
        MapEntity map = MapEntity.create(
            "지도 " + id, "설명", null, MapType.COMMUNITY, ownerId, tags, null
        );
        ReflectionTestUtils.setField(map, "id", id);
        ReflectionTestUtils.setField(map, "memberCount", memberCount);
        ReflectionTestUtils.setField(map, "createdAt", createdAt);
        return map;
    }

    static MapEntity map(long id, List<String> tags, int memberCount) {
        return map(id, 100L + id, tags, memberCount, NOW);
    }

    static RecommendationProperties properties() {
        return new RecommendationProperties(
            5,
            20,
            300,
            new RecommendationProperties.Weights(0.55, 0.25, 0.20),
            new RecommendationProperties.Diversity(1, 0.15),
            new RecommendationProperties.Profile(1.5, 60, 90)
        );
    }
}
