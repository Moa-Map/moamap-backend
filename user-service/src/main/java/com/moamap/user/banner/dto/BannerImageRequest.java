package com.moamap.user.banner.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 배너 등록·이미지 교체. 이미지 업로드 주소 발급 응답의 fileUrl을 그대로 보낸다.
 */
public record BannerImageRequest(

        @Schema(description = "업로드 주소 발급 응답의 fileUrl")
        @NotBlank(message = "이미지 주소는 필수입니다.")
        @Size(max = 1000, message = "이미지 주소가 너무 깁니다.")
        String imageUrl
) {
}
