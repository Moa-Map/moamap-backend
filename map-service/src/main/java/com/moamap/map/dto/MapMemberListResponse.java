package com.moamap.map.dto;

import java.util.List;

/**
 * 지도 멤버 전체 목록. memberCount는 members의 크기와 같지만, 화면 상단의 "N명 참여 중"을
 * 목록 길이에 의존하지 않고 그대로 쓸 수 있도록 함께 내려준다.
 */
public record MapMemberListResponse(
    int memberCount,
    List<MapMemberSummaryResponse> members
) {

    public static MapMemberListResponse of(List<MapMemberSummaryResponse> members) {
        return new MapMemberListResponse(members.size(), List.copyOf(members));
    }
}
