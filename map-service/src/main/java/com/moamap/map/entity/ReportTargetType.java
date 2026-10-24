package com.moamap.map.entity;

/**
 * map-service가 받는 신고의 대상 종류.
 */
public enum ReportTargetType {
    /** 지도 자체(이름·소개·커버 사진) */
    MAP,
    /** 로그(지도 게시물) */
    MAP_POST,
    /** 로그 댓글 */
    MAP_POST_COMMENT
}
