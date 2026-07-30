package com.moamap.map.recommendation;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 사용자의 관심 태그와 그 강도.
 *
 * 태그를 단순 집합으로 다루면 세 지도에 모두 있는 태그와 한 지도에만 있는 태그가 같은 무게가 된다.
 * 그래서 태그별 가중치를 가진 벡터로 표현하고, 지도와의 유사도는 코사인 유사도로 잰다.
 */
public record InterestProfile(Map<String, Double> weights) {

    private static final InterestProfile EMPTY = new InterestProfile(Map.of());

    public InterestProfile {
        weights = Map.copyOf(weights);
    }

    /** 참여한 지도가 없는 사용자(신규 가입자·비로그인)의 프로필. */
    public static InterestProfile empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return weights.isEmpty();
    }

    public Set<String> tags() {
        return weights.keySet();
    }

    /**
     * 관심 벡터와 지도 태그의 코사인 유사도(0~1).
     *
     * 지도 태그는 있고/없고만 있으므로 1로 두고, 관심 벡터의 가중치와 내적한 뒤 두 벡터 크기로 나눈다.
     * 겹친 개수만 세는 방식과 달리 "내가 중요하게 여기는 태그가 겹쳤는지"가 반영된다.
     */
    public double similarity(Collection<String> mapTags) {
        if (weights.isEmpty() || mapTags == null || mapTags.isEmpty()) {
            return 0.0;
        }

        double dotProduct = 0.0;
        for (String tag : Set.copyOf(mapTags)) {
            Double weight = weights.get(tag);
            if (weight != null) {
                dotProduct += weight;
            }
        }
        if (dotProduct == 0.0) {
            return 0.0;
        }

        double profileNorm = Math.sqrt(weights.values().stream().mapToDouble(w -> w * w).sum());
        double mapNorm = Math.sqrt(Set.copyOf(mapTags).size());
        return dotProduct / (profileNorm * mapNorm);
    }

    /** 지도 태그 중 관심사와 겹치는 것들. 추천 이유 문구에 쓴다. */
    public List<String> matchedTags(Collection<String> mapTags) {
        if (mapTags == null) {
            return List.of();
        }
        return mapTags.stream()
            .distinct()
            .filter(weights::containsKey)
            .sorted((a, b) -> Double.compare(weights.get(b), weights.get(a)))
            .toList();
    }
}
