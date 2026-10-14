package com.moamap.user.inquiry.entity;

/**
 * 문의 유형. 앱의 선택 목록과 1:1로 맞춘다.
 */
public enum InquiryType {
    /** 서비스 이용 문제 */
    SERVICE,
    /** 기술적 문제 */
    TECHNICAL,
    /** 계정 관련 */
    ACCOUNT,
    /** 신고 및 제재 */
    REPORT,
    /** 제휴 문의 */
    PARTNERSHIP,
    /** 기타 */
    OTHER
}
