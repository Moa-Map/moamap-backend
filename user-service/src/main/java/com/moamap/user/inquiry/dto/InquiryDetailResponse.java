package com.moamap.user.inquiry.dto;

import com.moamap.user.inquiry.entity.Inquiry;
import com.moamap.user.inquiry.entity.InquiryStatus;
import com.moamap.user.inquiry.entity.InquiryType;
import java.time.Instant;

/**
 * 관리자 상세. 답장을 보내야 하므로 이메일과 본문 전체를 담는다. 관리자 API로만 나간다.
 */
public record InquiryDetailResponse(
        Long inquiryId,
        Long userId,
        InquiryType type,
        InquiryStatus status,
        String replyEmail,
        String content,
        Instant createdAt,
        Instant updatedAt,
        Instant answeredAt
) {

    public static InquiryDetailResponse from(Inquiry inquiry) {
        return new InquiryDetailResponse(inquiry.getId(), inquiry.getUserId(), inquiry.getType(),
                inquiry.getStatus(), inquiry.getReplyEmail(), inquiry.getContent(),
                inquiry.getCreatedAt(), inquiry.getUpdatedAt(), inquiry.getAnsweredAt());
    }
}
