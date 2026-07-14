package com.moamap.map.entity;

/**
 * 지도 유형.
 * - OFFICIAL  : 공공데이터 기반 공식 지도(시스템 관리, 사용자가 생성 불가)
 * - COMMUNITY : 공개 커뮤니티 지도(누구나 참여 가능)
 * - PRIVATE   : 프라이빗 지도(초대 코드로만 합류 가능)
 */
public enum MapType {
    OFFICIAL,
    COMMUNITY,
    PRIVATE
}
