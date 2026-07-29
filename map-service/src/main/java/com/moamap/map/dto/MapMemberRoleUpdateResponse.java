package com.moamap.map.dto;

import com.moamap.map.entity.MapRole;

/**
 * 멤버 역할 변경 응답. place-service의 MapMemberResponse와는 별개의 클라이언트용 계약이다.
 */
public record MapMemberRoleUpdateResponse(
    Long mapId,
    Long userId,
    MapRole role
) {

    public static MapMemberRoleUpdateResponse of(Long mapId, Long userId, MapRole role) {
        return new MapMemberRoleUpdateResponse(mapId, userId, role);
    }
}
