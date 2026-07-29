package com.moamap.map.dto;

import java.util.List;
import com.moamap.map.entity.MapEntity;
import com.moamap.map.entity.MapType;

/**
 * 목록 화면용 지도 요약. joined는 요청자의 참여 여부.
 */
public record MapSummaryResponse(
    Long id,
    String name,
    String description,
    String imageUrl,
    MapType type,
    List<String> tags,
    int memberCount,
    boolean joined,
    int placeCount
) {

    public static MapSummaryResponse of(MapEntity map, boolean joined) {
        return new MapSummaryResponse(
            map.getId(),
            map.getName(),
            map.getDescription(),
            map.getImageUrl(),
            map.getType(),
            List.copyOf(map.getTags()),
            map.getMemberCount(),
            joined,
            map.getPlaceCount()
        );
    }
}
