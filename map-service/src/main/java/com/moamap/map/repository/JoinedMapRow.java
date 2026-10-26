package com.moamap.map.repository;

import com.moamap.map.entity.MapEntity;
import com.moamap.map.entity.MapRole;

/**
 * 모음 탭 목록 한 행. 지도와 그 지도에서의 내 역할을 같은 조인 결과에서 함께 꺼낸다.
 */
public record JoinedMapRow(MapEntity map, MapRole role) {
}
