package com.moamap.map.dto;

import java.util.List;
import com.moamap.map.entity.MapEntity;
import com.moamap.map.entity.MapRole;
import com.moamap.map.entity.MapType;

/**
 * 모음(내가 참여한 지도) 목록용 지도 요약. 목록 요약에 요청자의 역할(myRole)을 더한다.
 * 내가 참여한 목록이므로 joined는 항상 true.
 */
public record MyMapSummaryResponse(
    Long id,
    String name,
    String description,
    String imageUrl,
    MapType type,
    List<String> tags,
    int memberCount,
    boolean joined,
    MapRole myRole,
    int placeCount,
    boolean personal
) {

    public static MyMapSummaryResponse of(MapEntity map, MapRole myRole) {
        return new MyMapSummaryResponse(
            map.getId(),
            map.getName(),
            map.getDescription(),
            map.getImageUrl(),
            map.getType(),
            List.copyOf(map.getTags()),
            map.getMemberCount(),
            true,
            myRole,
            map.getPlaceCount(),
            map.isPersonal()
        );
    }
}
