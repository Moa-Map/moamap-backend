package com.moamap.map.recommendation;

import java.util.List;
import com.moamap.map.entity.MapRole;
import org.junit.jupiter.api.Test;

import static com.moamap.map.recommendation.RecommendationFixtures.NOW;
import static com.moamap.map.recommendation.RecommendationFixtures.properties;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 참여 지도에서 관심 태그 가중치를 뽑아내는 규칙을 검증한다.
 */
class UserInterestProfilerTest {

    private final UserInterestProfiler profiler = new UserInterestProfiler(properties());

    @Test
    void 참여한_지도가_없으면_빈_프로필이다() {
        InterestProfile profile = profiler.profile(List.of(), NOW);

        assertThat(profile.isEmpty()).isTrue();
    }

    @Test
    void 여러_지도에_공통으로_있는_태그가_더_높은_가중치를_갖는다() {
        List<JoinedMap> joined = List.of(
            new JoinedMap(List.of("맛집", "카페"), MapRole.MEMBER, NOW),
            new JoinedMap(List.of("맛집", "데이트"), MapRole.MEMBER, NOW)
        );

        InterestProfile profile = profiler.profile(joined, NOW);

        assertThat(profile.weights().get("맛집"))
            .isGreaterThan(profile.weights().get("카페"));
    }

    @Test
    void 직접_만든_지도의_태그가_단순_참여보다_가중치가_높다() {
        InterestProfile owner = profiler.profile(
            List.of(new JoinedMap(List.of("캠핑"), MapRole.OWNER, NOW)), NOW);
        InterestProfile member = profiler.profile(
            List.of(new JoinedMap(List.of("캠핑"), MapRole.MEMBER, NOW)), NOW);

        assertThat(owner.weights().get("캠핑"))
            .isGreaterThan(member.weights().get("캠핑"));
    }

    @Test
    void 오래_전에_참여한_지도일수록_가중치가_낮아진다() {
        InterestProfile recent = profiler.profile(
            List.of(new JoinedMap(List.of("전시"), MapRole.MEMBER, NOW)), NOW);
        InterestProfile old = profiler.profile(
            List.of(new JoinedMap(List.of("전시"), MapRole.MEMBER, NOW.minusDays(180))), NOW);

        assertThat(old.weights().get("전시"))
            .isLessThan(recent.weights().get("전시"));
    }

    @Test
    void 같은_지도_안에서_태그가_중복돼도_한_번만_반영된다() {
        InterestProfile duplicated = profiler.profile(
            List.of(new JoinedMap(List.of("러닝", "러닝"), MapRole.MEMBER, NOW)), NOW);
        InterestProfile single = profiler.profile(
            List.of(new JoinedMap(List.of("러닝"), MapRole.MEMBER, NOW)), NOW);

        assertThat(duplicated.weights().get("러닝"))
            .isEqualTo(single.weights().get("러닝"));
    }

    @Test
    void 빈_문자열_태그는_무시한다() {
        InterestProfile profile = profiler.profile(
            List.of(new JoinedMap(List.of("맛집", "  ", ""), MapRole.MEMBER, NOW)), NOW);

        assertThat(profile.tags()).containsExactly("맛집");
    }
}
