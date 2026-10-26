package com.moamap.map.dto;

import com.moamap.map.entity.MapType;

/**
 * 지도 생성 시 사용자가 고르는 공개 범위. 내부 MapType으로 변환된다.
 */
public enum MapVisibility {

    PUBLIC(MapType.COMMUNITY),
    PRIVATE(MapType.PRIVATE);

    private final MapType mapType;

    MapVisibility(MapType mapType) {
        this.mapType = mapType;
    }

    public MapType toMapType() {
        return mapType;
    }
}
