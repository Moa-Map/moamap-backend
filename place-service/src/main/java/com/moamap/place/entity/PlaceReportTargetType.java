package com.moamap.place.entity;

/**
 * place-service가 받는 신고의 대상 종류. 운영자 신고 목록을 거를 때 쓴다.
 */
public enum PlaceReportTargetType {
    /** 장소(사진·메모·태그) */
    PLACE,
    /** 장소 댓글(내용·사진) */
    PLACE_COMMENT
}
