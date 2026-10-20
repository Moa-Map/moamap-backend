package com.moamap.user.inquiry.dto;

import com.moamap.user.inquiry.entity.Inquiry;
import com.moamap.user.inquiry.entity.InquiryStatus;
import com.moamap.user.inquiry.entity.InquiryType;
import java.time.Instant;

/**
 * 관리자 목록 한 줄. 답변 이메일과 본문 전체는 넣지 않는다 — 목록에서 개인정보를 한꺼번에 내보내지 않기 위해서다.
 * 전체 내용과 이메일은 상세 조회에서만 준다.
 */
public record InquirySummaryResponse(
        Long inquiryId,
        InquiryType type,
        InquiryStatus status,
        String preview,
        Instant createdAt,
        Instant answeredAt
) {

    static final int PREVIEW_LENGTH = 50;

    public static InquirySummaryResponse from(Inquiry inquiry) {
        return new InquirySummaryResponse(inquiry.getId(), inquiry.getType(), inquiry.getStatus(),
                preview(inquiry.getContent()), inquiry.getCreatedAt(), inquiry.getAnsweredAt());
    }

    /** 한글·이모지가 반으로 잘리지 않게 문자(코드포인트) 단위로 자른다. */
    private static String preview(String content) {
        if (content.codePointCount(0, content.length()) <= PREVIEW_LENGTH) {
            return content;
        }
        return content.substring(0, content.offsetByCodePoints(0, PREVIEW_LENGTH)) + "…";
    }
}
