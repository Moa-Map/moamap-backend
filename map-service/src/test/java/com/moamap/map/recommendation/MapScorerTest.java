package com.moamap.map.recommendation;

import java.util.List;
import java.util.Map;
import com.moamap.map.entity.MapEntity;
import org.junit.jupiter.api.Test;

import static com.moamap.map.recommendation.RecommendationFixtures.NOW;
import static com.moamap.map.recommendation.RecommendationFixtures.map;
import static com.moamap.map.recommendation.RecommendationFixtures.properties;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 점수 구성 요소(태그 유사도·인기도·신선도)가 의도대로 순위에 반영되는지 검증한다.
 */
class MapScorerTest {

    private final MapScorer scorer = new MapScorer(properties());

    @Test
    void 관심_태그가_많이_겹치는_지도가_더_높은_점수를_받는다() {
        InterestProfile profile = new InterestProfile(Map.of("맛집", 1.0, "카페", 1.0));
        MapEntity matched = map(1L, List.of("맛집", "카페"), 10);
        MapEntity unmatched = map(2L, List.of("캠핑"), 10);

        List<ScoredMap> scored = scorer.score(List.of(unmatched, matched), profile, NOW);

        assertThat(scored.get(0).map().getId()).isEqualTo(1L);
    }

    @Test
    void 관심도가_높은_태그가_겹칠수록_점수가_높다() {
        InterestProfile profile = new InterestProfile(Map.of("맛집", 3.0, "캠핑", 0.5));
        MapEntity strong = map(1L, List.of("맛집"), 10);
        MapEntity weak = map(2L, List.of("캠핑"), 10);

        List<ScoredMap> scored = scorer.score(List.of(weak, strong), profile, NOW);

        assertThat(scored.get(0).map().getId()).isEqualTo(1L);
    }

    @Test
    void 태그가_같으면_멤버가_많은_지도가_더_높은_점수를_받는다() {
        InterestProfile profile = new InterestProfile(Map.of("맛집", 1.0));
        MapEntity popular = map(1L, List.of("맛집"), 1000);
        MapEntity quiet = map(2L, List.of("맛집"), 5);

        List<ScoredMap> scored = scorer.score(List.of(quiet, popular), profile, NOW);

        assertThat(scored.get(0).map().getId()).isEqualTo(1L);
    }

    @Test
    void 인기도는_로그로_정규화되어_격차가_완만해진다() {
        InterestProfile empty = InterestProfile.empty();
        MapEntity huge = map(1L, 101L, List.of("맛집"), 10_000, NOW);
        MapEntity small = map(2L, 102L, List.of("맛집"), 100, NOW);

        List<ScoredMap> scored = scorer.score(List.of(huge, small), empty, NOW);

        // 멤버 수는 100배 차이지만 점수 차이는 2배를 넘지 않는다.
        assertThat(scored.get(0).score()).isLessThan(scored.get(1).score() * 2);
    }

    @Test
    void 태그와_인기도가_같으면_최근에_만들어진_지도가_앞선다() {
        InterestProfile profile = new InterestProfile(Map.of("맛집", 1.0));
        MapEntity fresh = map(1L, 101L, List.of("맛집"), 10, NOW);
        MapEntity old = map(2L, 102L, List.of("맛집"), 10, NOW.minusDays(365));

        List<ScoredMap> scored = scorer.score(List.of(old, fresh), profile, NOW);

        assertThat(scored.get(0).map().getId()).isEqualTo(1L);
    }

    @Test
    void 관심_프로필이_비어도_인기도와_신선도로_순서가_매겨진다() {
        MapEntity popular = map(1L, 101L, List.of("맛집"), 500, NOW);
        MapEntity quiet = map(2L, 102L, List.of("캠핑"), 1, NOW.minusDays(300));

        List<ScoredMap> scored = scorer.score(List.of(quiet, popular), InterestProfile.empty(), NOW);

        assertThat(scored.get(0).map().getId()).isEqualTo(1L);
        assertThat(scored.get(0).score()).isGreaterThan(0);
    }

    @Test
    void 겹친_태그를_추천_이유용으로_함께_돌려준다() {
        InterestProfile profile = new InterestProfile(Map.of("맛집", 2.0, "카페", 1.0));
        MapEntity target = map(1L, List.of("맛집", "카페", "캠핑"), 10);

        List<ScoredMap> scored = scorer.score(List.of(target), profile, NOW);

        assertThat(scored.get(0).matchedTags()).containsExactly("맛집", "카페");
    }

    @Test
    void 후보가_없으면_빈_목록을_돌려준다() {
        assertThat(scorer.score(List.of(), InterestProfile.empty(), NOW)).isEmpty();
    }

    @Test
    void 콜드스타트에서는_멤버_수가_많고_오래된_지도가_적고_최신인_지도보다_앞선다() {
        MapEntity manyOld = map(1L, 101L, List.of("러닝"), 3200, NOW.minusDays(90));
        MapEntity fewNew = map(2L, 102L, List.of("혼밥"), 980, NOW.minusDays(5));

        List<ScoredMap> scored = scorer.score(List.of(fewNew, manyOld), InterestProfile.empty(), NOW);

        assertThat(scored.get(0).map().getId()).isEqualTo(1L);
    }
}
