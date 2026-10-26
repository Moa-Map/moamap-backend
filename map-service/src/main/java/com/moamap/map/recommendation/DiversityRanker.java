package com.moamap.map.recommendation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.moamap.map.entity.MapEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 점수 상위만 그대로 자르면 추천 다섯 개가 전부 같은 주제가 되기 쉽다.
 * 이미 고른 지도와 겹칠수록 감점해가며 하나씩 뽑아, 점수와 다양성을 함께 만족시킨다.
 *
 * - 태그가 많이 겹칠수록 감점한다.
 * - 같은 사람이 만든 지도는 정해진 개수까지만 넣는다.
 */
@Component
@RequiredArgsConstructor
public class DiversityRanker {

    private final RecommendationProperties properties;

    public List<ScoredMap> rank(List<ScoredMap> scoredMaps, int size) {
        if (scoredMaps == null || scoredMaps.isEmpty() || size <= 0) {
            return List.of();
        }

        RecommendationProperties.Diversity config = properties.diversity();
        List<ScoredMap> remaining = new ArrayList<>(scoredMaps);
        List<ScoredMap> selected = new ArrayList<>(size);
        List<Set<String>> selectedTagSets = new ArrayList<>(size);
        Map<Long, Integer> ownerCounts = new HashMap<>();

        while (selected.size() < size && !remaining.isEmpty()) {
            ScoredMap best = null;
            double bestAdjusted = Double.NEGATIVE_INFINITY;

            for (ScoredMap candidate : remaining) {
                if (exceedsOwnerLimit(candidate.map(), ownerCounts, config.maxPerOwner())) {
                    continue;
                }
                double adjusted = candidate.score()
                    - overlapRatio(candidate.map(), selectedTagSets) * config.tagOverlapPenalty();
                if (adjusted > bestAdjusted) {
                    bestAdjusted = adjusted;
                    best = candidate;
                }
            }

            if (best == null) {
                // 소유자 제한 때문에 더 고를 후보가 없다. 남은 자리는 채우지 않는다.
                break;
            }
            selected.add(best);
            selectedTagSets.add(tagSet(best.map()));
            ownerCounts.merge(best.map().getOwnerId(), 1, Integer::sum);
            remaining.remove(best);
        }
        return List.copyOf(selected);
    }

    private boolean exceedsOwnerLimit(MapEntity map, Map<Long, Integer> ownerCounts, int maxPerOwner) {
        if (maxPerOwner <= 0) {
            return false;
        }
        return ownerCounts.getOrDefault(map.getOwnerId(), 0) >= maxPerOwner;
    }

    /**
     * 이미 선택된 지도들과의 태그 겹침 정도 중 가장 큰 값(0~1).
     */
    private double overlapRatio(MapEntity candidate, List<Set<String>> selectedTagSets) {
        Set<String> candidateTags = tagSet(candidate);
        if (candidateTags.isEmpty() || selectedTagSets.isEmpty()) {
            return 0.0;
        }

        double maxRatio = 0.0;
        for (Set<String> selectedTags : selectedTagSets) {
            long overlapped = candidateTags.stream().filter(selectedTags::contains).count();
            maxRatio = Math.max(maxRatio, (double) overlapped / candidateTags.size());
        }
        return maxRatio;
    }

    private Set<String> tagSet(MapEntity map) {
        List<String> tags = map.getTags();
        return (tags == null) ? Set.of() : new HashSet<>(tags);
    }
}
