package com.moamap.user.inquiry.dto;

import com.moamap.user.inquiry.entity.InquiryType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record InquiryCreateRequest(

        @Schema(description = "문의 유형", example = "TECHNICAL")
        @NotNull(message = "문의 유형은 필수입니다.")
        InquiryType type,

        // 기본 @Email은 "a@b"처럼 도메인에 점이 없는 값도 통과시킨다. 실제로 답장을 보낼 수 있는 형태만 받는다.
        @Schema(description = "답변 받을 이메일", example = "user@example.com")
        @NotBlank(message = "답변 받을 이메일은 필수입니다.")
        @Email(regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = 254, message = "이메일은 254자 이하여야 합니다.")
        String replyEmail,

        @Schema(description = "문의 내용(일반 텍스트)", example = "지도에 장소가 추가되지 않아요.")
        @NotBlank(message = "문의 내용은 필수입니다.")
        @Size(max = 2000, message = "문의 내용은 2000자 이하여야 합니다.")
        String content
) {
}
