package com.moamap.map.entity;

/**
 * 지도 내 멤버 역할.
 * NONE은 "해당 지도에 가입하지 않은 상태"를 표현하기 위한 값으로, 영속화되지 않는다.
 */
public enum MapRole {
    OWNER,
    ADMIN,
    MEMBER,
    NONE
}
