package com.moamap.user.banner.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record BannerImageUploadUrlRequest(

        @Schema(description = "image/jpeg, image/png, image/webp", example = "image/jpeg")
        @NotBlank(message = "파일 형식은 필수입니다.")
        String contentType,

        @Schema(description = "파일 크기(바이트). 10MB 이하", example = "524288")
        @NotNull(message = "파일 크기는 필수입니다.")
        @Positive(message = "파일 크기는 0보다 커야 합니다.")
        Long fileSize
) {
}
