package com.moamap.map.dto;

import com.moamap.map.entity.MapEntity;
import com.moamap.map.entity.MapRole;
import com.moamap.map.entity.MapType;

/**
 * 멤버 역할 조회 응답. place-service가 장소 등록 승인 여부와 URL 등록 허용 여부를 판단할 때 사용한다.
 *
 * place-service는 이 응답을 자체 record(MapMemberResponse)로 역직렬화한다. 필드를 바꾸면 그쪽도 함께 고친다.
 */
public record MapMemberRoleResponse(
    MapType mapType,
    MapRole role,
    boolean allowUrlImport
) {

    public static MapMemberRoleResponse of(MapEntity map, MapRole role) {
        return new MapMemberRoleResponse(map.getType(), role, map.isUrlImportAllowed());
    }
}
