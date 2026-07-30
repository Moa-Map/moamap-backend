package com.moamap.map.recommendation;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import com.moamap.map.entity.MapEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 후보 지도에 점수를 매긴다.
 *
 * 점수 = 태그 유사도 x w1 + 인기도 x w2 + 신선도 x w3 (각 요소는 0~1로 정규화)
 *
 * 참여 이력이 없어 관심 프로필이 비어 있으면 태그 항목이 항상 0이 되어 모든 후보가 같은 점수를 받는다.
 * 그래서 이때는 설정된 콜드스타트 가중치(popularity/freshness)를 그대로 쓴다.
 */
@Component
@RequiredArgsConstructor
public class MapScorer {

    private final RecommendationProperties properties;

    public List<ScoredMap> score(List<MapEntity> candidates, InterestProfile profile, LocalDateTime now) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        long maxMemberCount = candidates.stream()
            .mapToLong(MapEntity::getMemberCount)
            .max()
            .orElse(0L);
        Weights weights = resolveWeights(profile);

        return candidates.stream()
            .map(map -> new ScoredMap(
                map,
                scoreOf(map, profile, weights, maxMemberCount, now),
                profile.matchedTags(map.getTags())
            ))
            .sorted(Comparator.comparingDouble(ScoredMap::score).reversed())
            .toList();
    }

    private double scoreOf(MapEntity map, InterestProfile profile, Weights weights,
                           long maxMemberCount, LocalDateTime now) {
        double tagScore = profile.similarity(map.getTags());
        double popularityScore = popularity(map.getMemberCount(), maxMemberCount);
        double freshnessScore = freshness(map.getCreatedAt(), now);

        return tagScore * weights.tag()
            + popularityScore * weights.popularity()
            + freshnessScore * weights.freshness();
    }

    /**
     * 멤버 수를 로그로 눌러 정규화한다.
     *
     * 원본 값을 그대로 쓰면 멤버가 수천 명인 지도가 다른 요소를 압도해 개인화가 무의미해진다.
     * 로그를 씌우면 큰 지도가 여전히 유리하되 격차가 완만해진다.
     */
    private double popularity(int memberCount, long maxMemberCount) {
        if (maxMemberCount <= 0) {
            return 0.0;
        }
        return Math.log1p(Math.max(0, memberCount)) / Math.log1p(maxMemberCount);
    }

    /**
     * 만들어진 지 오래될수록 0에 가까워지는 지수 감쇠.
     */
    private double freshness(LocalDateTime createdAt, LocalDateTime now) {
        if (createdAt == null || now == null) {
            return 0.0;
        }
        double days = Math.max(0, Duration.between(createdAt, now).toDays());
        return Math.exp(-days / properties.profile().freshnessHalfLifeDays());
    }

    /**
     * 관심 프로필이 비어 있으면(콜드스타트) 태그 몫은 0이고, 나머지는 설정값을 그대로 쓴다.
     * 비례 배분은 아무도 결정하지 않은 파생값을 만들어내므로 쓰지 않는다.
     */
    private Weights resolveWeights(InterestProfile profile) {
        RecommendationProperties.Weights configured = properties.weights();
        if (!profile.isEmpty()) {
            return new Weights(configured.tag(), configured.popularity(), configured.freshness());
        }
        RecommendationProperties.Weights.ColdStart coldStart = configured.coldStart();
        return new Weights(0, coldStart.popularity(), coldStart.freshness());
    }

    private record Weights(double tag, double popularity, double freshness) {
    }
}
