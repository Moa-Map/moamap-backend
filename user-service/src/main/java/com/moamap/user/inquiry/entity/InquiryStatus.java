package com.moamap.user.inquiry.entity;

/**
 * 문의 처리 상태. 답장은 관리자가 메일로 직접 보내고, 보낸 뒤 ANSWERED로 바꾼다.
 */
public enum InquiryStatus {
    RECEIVED,
    IN_PROGRESS,
    ANSWERED
}
