package com.moamap.map.dto;

import com.moamap.map.entity.MapMember;
import com.moamap.map.entity.MapRole;
import com.moamap.map.user.dto.UserProfileResponse;

/**
 * 멤버 관리 화면의 한 줄. nickname/profileImageUrl은 user-service 조회가 실패하면 null로 내려간다.
 */
public record MapMemberSummaryResponse(
    Long userId,
    String nickname,
    String profileImageUrl,
    MapRole role
) {

    public static MapMemberSummaryResponse of(MapMember member, UserProfileResponse profile) {
        return new MapMemberSummaryResponse(
            member.getUserId(),
            (profile != null) ? profile.nickname() : null,
            (profile != null) ? profile.profileImageUrl() : null,
            member.getRole()
        );
    }
}
