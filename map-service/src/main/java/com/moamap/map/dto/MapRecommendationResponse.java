package com.moamap.map.dto;

import java.util.List;
import com.moamap.map.entity.MapEntity;
import com.moamap.map.entity.MapType;

/**
 * 홈 화면 추천 캐러셀용 응답. 목록 응답에 추천 이유(reason)를 더한 형태다.
 *
 * reason은 사용자가 추천을 납득하게 하고, 추천이 이상할 때 원인을 찾는 단서도 된다.
 * 비공개 지도의 이름이 새지 않도록 태그 수준으로만 표현한다.
 */
public record MapRecommendationResponse(
    Long id,
    String name,
    String description,
    String imageUrl,
    MapType type,
    List<String> tags,
    int memberCount,
    String reason
) {

    public static MapRecommendationResponse of(MapEntity map, String reason) {
        return new MapRecommendationResponse(
            map.getId(),
            map.getName(),
            map.getDescription(),
            map.getImageUrl(),
            map.getType(),
            List.copyOf(map.getTags()),
            map.getMemberCount(),
            reason
        );
    }
}
