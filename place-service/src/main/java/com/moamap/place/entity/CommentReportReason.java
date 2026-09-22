package com.moamap.place.entity;

/**
 * 댓글 신고 사유. 어느 사유로 신고가 몰리는지를 보고 나중에 자동 처리 기준을 정하기 위해 분류해 둔다.
 */
public enum CommentReportReason {
    /** 광고·홍보·도배 */
    SPAM,
    /** 욕설·비하·혐오 표현 */
    ABUSE,
    /** 음란물·선정적 내용 */
    SEXUAL,
    /** 장소와 무관하거나 사실과 다른 내용 */
    FALSE_INFO,
    /** 위 분류에 없는 사유. detail에 직접 적는다. */
    OTHER
}
