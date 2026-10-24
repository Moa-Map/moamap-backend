package com.moamap.user.report.entity;

/**
 * 신고 사유. place-service·map-service의 신고 사유와 값을 맞춘다(앱이 같은 목록을 쓴다).
 */
public enum ReportReason {
    /** 스팸·광고·도배 */
    SPAM,
    /** 욕설·비하·혐오 */
    ABUSE,
    /** 음란·선정적 내용(부적절한 사진 포함) */
    SEXUAL,
    /** 개인정보 노출 */
    PRIVACY,
    /** 사실과 다름 */
    FALSE_INFO,
    /** 기타. detail에 직접 적는다. */
    OTHER
}
