package com.moamap.map.dto;

import java.time.LocalDateTime;
import java.util.List;
import com.moamap.map.entity.MapEntity;
import com.moamap.map.entity.MapRole;
import com.moamap.map.entity.MapType;

/**
 * 상세 화면용 지도 응답.
 * myRole은 요청자의 역할(NONE=미참여)이며, inviteCode는 관리 권한자(OWNER/ADMIN)에게만 채워진다.
 */
public record MapDetailResponse(
    Long id,
    String name,
    String description,
    String imageUrl,
    MapType type,
    Long ownerId,
    List<String> tags,
    int memberCount,
    boolean joined,
    MapRole myRole,
    String inviteCode,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {

    public static MapDetailResponse of(MapEntity map, MapRole myRole, String inviteCode) {
        return new MapDetailResponse(
            map.getId(),
            map.getName(),
            map.getDescription(),
            map.getImageUrl(),
            map.getType(),
            map.getOwnerId(),
            List.copyOf(map.getTags()),
            map.getMemberCount(),
            myRole != MapRole.NONE,
            myRole,
            inviteCode,
            map.getCreatedAt(),
            map.getUpdatedAt()
        );
    }
}
