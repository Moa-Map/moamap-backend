package com.moamap.map.recommendation;

import java.util.List;
import com.moamap.map.entity.MapEntity;
import org.junit.jupiter.api.Test;

import static com.moamap.map.recommendation.RecommendationFixtures.NOW;
import static com.moamap.map.recommendation.RecommendationFixtures.map;
import static com.moamap.map.recommendation.RecommendationFixtures.properties;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 점수 순으로만 자르면 결과가 한 주제로 쏠리는 문제를 다양성 보정이 막아주는지 검증한다.
 */
class DiversityRankerTest {

    private final DiversityRanker ranker = new DiversityRanker(properties());

    @Test
    void 점수가_비슷하면_태그가_겹치지_않는_지도가_먼저_선택된다() {
        List<ScoredMap> scored = List.of(
            new ScoredMap(map(1L, 101L, List.of("맛집"), 10, NOW), 0.90, List.of("맛집")),
            new ScoredMap(map(2L, 102L, List.of("맛집"), 10, NOW), 0.89, List.of("맛집")),
            new ScoredMap(map(3L, 103L, List.of("캠핑"), 10, NOW), 0.85, List.of())
        );

        List<ScoredMap> ranked = ranker.rank(scored, 2);

        assertThat(ranked).extracting(s -> s.map().getId()).containsExactly(1L, 3L);
    }

    @Test
    void 같은_소유자의_지도는_한_개만_선택된다() {
        List<ScoredMap> scored = List.of(
            new ScoredMap(map(1L, 500L, List.of("맛집"), 10, NOW), 0.90, List.of()),
            new ScoredMap(map(2L, 500L, List.of("캠핑"), 10, NOW), 0.88, List.of()),
            new ScoredMap(map(3L, 700L, List.of("전시"), 10, NOW), 0.50, List.of())
        );

        List<ScoredMap> ranked = ranker.rank(scored, 3);

        assertThat(ranked).extracting(s -> s.map().getOwnerId()).containsExactly(500L, 700L);
    }

    @Test
    void 태그가_겹치지_않으면_점수_순서가_그대로_유지된다() {
        List<ScoredMap> scored = List.of(
            new ScoredMap(map(1L, 101L, List.of("맛집"), 10, NOW), 0.90, List.of()),
            new ScoredMap(map(2L, 102L, List.of("캠핑"), 10, NOW), 0.80, List.of()),
            new ScoredMap(map(3L, 103L, List.of("전시"), 10, NOW), 0.70, List.of())
        );

        List<ScoredMap> ranked = ranker.rank(scored, 3);

        assertThat(ranked).extracting(s -> s.map().getId()).containsExactly(1L, 2L, 3L);
    }

    @Test
    void 요청한_개수보다_후보가_적으면_있는_만큼만_돌려준다() {
        List<ScoredMap> scored = List.of(
            new ScoredMap(map(1L, 101L, List.of("맛집"), 10, NOW), 0.90, List.of())
        );

        assertThat(ranker.rank(scored, 5)).hasSize(1);
    }

    @Test
    void 후보가_없거나_요청_개수가_0이면_빈_목록이다() {
        assertThat(ranker.rank(List.of(), 5)).isEmpty();
        assertThat(ranker.rank(
            List.of(new ScoredMap(map(1L, List.of("맛집"), 10), 0.9, List.of())), 0)).isEmpty();
    }
}
