package com.moamap.map.recommendation;

import java.util.List;
import com.moamap.map.entity.MapEntity;

/**
 * 점수가 매겨진 추천 후보. matchedTags는 추천 이유 문구를 만드는 데 쓴다.
 */
public record ScoredMap(MapEntity map, double score, List<String> matchedTags) {

    public ScoredMap {
        matchedTags = List.copyOf(matchedTags);
    }
}
