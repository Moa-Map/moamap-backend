package com.moamap.place.entity;

/**
 * 신고 사유. 장소·장소 댓글 신고가 함께 쓰고, user-service·map-service의 신고 사유와 값을 맞춘다(앱이 같은 목록을 쓴다).
 */
public enum ReportReason {
    /** 광고·홍보·도배 */
    SPAM,
    /** 욕설·비하·혐오 표현 */
    ABUSE,
    /** 음란물·선정적 내용(부적절한 사진 포함) */
    SEXUAL,
    /** 개인정보 노출 */
    PRIVACY,
    /** 장소와 무관하거나 사실과 다른 내용(폐업, 위치·정보가 틀림 등) */
    FALSE_INFO,
    /** 위 분류에 없는 사유. detail에 직접 적는다. */
    OTHER
}
