package com.moamap.place.map.dto;

/**
 * map-service의 MapMemberRoleResponse를 받는 복사본. 필드를 바꾸면 그쪽도 함께 고친다.
 */
public record MapMemberResponse(MapType mapType, MapMemberRole role, boolean allowUrlImport) {
}
