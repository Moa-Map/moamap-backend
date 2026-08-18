package com.moamap.map.dto;

import com.moamap.map.entity.MapMember;
import com.moamap.map.entity.MapRole;
import com.moamap.map.user.dto.UserProfileResponse;

/**
 * 멤버 관리 화면의 한 줄. nickname/profileImageUrl은 user-service 조회가 실패하면 null로 내려간다.
 * placeCount도 같은 규칙이다 — 0은 "등록한 장소가 없다"는 뜻이고, 못 받아왔을 때는 null로 내려간다.
 */
public record MapMemberSummaryResponse(
    Long userId,
    String nickname,
    String profileImageUrl,
    MapRole role,
    Long placeCount
) {

    public static MapMemberSummaryResponse of(MapMember member, UserProfileResponse profile, Long placeCount) {
        return new MapMemberSummaryResponse(
            member.getUserId(),
            (profile != null) ? profile.nickname() : null,
            (profile != null) ? profile.profileImageUrl() : null,
            member.getRole(),
            placeCount
        );
    }
}
