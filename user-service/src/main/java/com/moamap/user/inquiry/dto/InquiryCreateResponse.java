package com.moamap.user.inquiry.dto;

import com.moamap.user.inquiry.entity.Inquiry;
import java.time.Instant;

/**
 * 등록 결과. 보낸 이메일·본문은 되돌려주지 않는다. 앱은 접수됐다는 사실만 알면 된다.
 */
public record InquiryCreateResponse(Long inquiryId, Instant createdAt) {

    public static InquiryCreateResponse from(Inquiry inquiry) {
        return new InquiryCreateResponse(inquiry.getId(), inquiry.getCreatedAt());
    }
}
