package com.moamap.place.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record MapShareExtractRequest(
    @Schema(description = "공유 링크. 앱 공유로 들어온 문구가 섞인 텍스트를 그대로 넣어도 된다.",
        example = "https://naver.me/xAbC1234")
    @NotBlank(message = "url은 필수입니다.") String url
) {
}
