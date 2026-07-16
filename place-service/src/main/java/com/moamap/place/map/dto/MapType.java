package com.moamap.place.map.dto;

/**
 * map-service의 MapType을 그대로 미러링한 값. 서비스 간 API 계약(응답 바디)일 뿐, map-service 코드에 직접 의존하지 않는다.
 */
public enum MapType {
    OFFICIAL,
    COMMUNITY,
    PRIVATE
}
