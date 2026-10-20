package com.moamap.user.inquiry.dto;

import com.moamap.user.inquiry.entity.InquiryStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record InquiryStatusUpdateRequest(

        @Schema(description = "바꿀 처리 상태", example = "ANSWERED")
        @NotNull(message = "처리 상태는 필수입니다.")
        InquiryStatus status
) {
}
