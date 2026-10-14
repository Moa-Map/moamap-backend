package com.moamap.user.notice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NoticeCreateRequest(

        @Schema(description = "제목", example = "서비스 점검 안내")
        @NotBlank(message = "제목은 필수입니다.")
        @Size(max = NoticeCreateRequest.MAX_TITLE, message = "제목은 100자 이하여야 합니다.")
        String title,

        @Schema(description = "본문(일반 텍스트)", example = "10월 20일 02:00~04:00 점검이 있습니다.")
        @NotBlank(message = "본문은 필수입니다.")
        @Size(max = NoticeCreateRequest.MAX_CONTENT, message = "본문은 5000자 이하여야 합니다.")
        String content
) {

    public static final int MAX_TITLE = 100;
    public static final int MAX_CONTENT = 5000;
}
