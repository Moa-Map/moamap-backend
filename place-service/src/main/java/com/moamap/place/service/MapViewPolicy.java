package com.moamap.place.service;

import com.moamap.common.exception.BusinessException;
import com.moamap.place.exception.PlaceErrorCode;
import com.moamap.place.map.dto.MapMemberResponse;
import com.moamap.place.map.dto.MapMemberRole;
import com.moamap.place.map.dto.MapType;
import org.springframework.stereotype.Component;

/**
 * 지도의 콘텐츠를 볼 수 있는지 판단한다. 공식·커뮤니티 지도는 로그인한 누구나, 프라이빗 지도는 멤버만 본다.
 *
 * mapType은 map-service 응답을 손으로 베낀 복사본이라 필드명이 어긋나면 null이 들어올 수 있다.
 * "PRIVATE가 아니면 통과"처럼 부정 조건으로 짜지 않고 유형을 하나씩 매칭시켜, 어디에도 걸리지 않으면 막는다
 * (PlaceActivityService와 같은 방식).
 */
@Component
public class MapViewPolicy {

    public void requireViewable(MapMemberResponse memberInfo) {
        if (memberInfo.mapType() == MapType.OFFICIAL || memberInfo.mapType() == MapType.COMMUNITY) {
            return;
        }
        if (memberInfo.mapType() == MapType.PRIVATE && isMember(memberInfo.role())) {
            return;
        }
        throw new BusinessException(PlaceErrorCode.NOT_MAP_MEMBER);
    }

    private static boolean isMember(MapMemberRole role) {
        return role == MapMemberRole.OWNER || role == MapMemberRole.ADMIN || role == MapMemberRole.MEMBER;
    }
}
