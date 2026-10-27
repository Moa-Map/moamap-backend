package com.moamap.map.dto;

import com.moamap.map.entity.MapRole;
import com.moamap.map.entity.MapType;

/**
 * 멤버 역할 조회 응답. place-service가 장소 등록 승인 여부를 판단할 때 사용한다.
 */
public record MapMemberRoleResponse(
    MapType mapType,
    MapRole role,
    boolean memberAutoApproval
) {

    public static MapMemberRoleResponse of(MapType mapType, MapRole role, boolean memberAutoApproval) {
        return new MapMemberRoleResponse(mapType, role, memberAutoApproval);
    }
}
