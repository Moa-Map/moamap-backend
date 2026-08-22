package com.moamap.map.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "로그 탭 게시물 사진 업로드 URL 발급 요청")
public record MapPostPhotoUploadUrlRequest(
    @Schema(description = "파일 MIME 타입", example = "image/jpeg")
    @NotBlank String contentType,

    @Schema(description = "파일 크기(byte). 장당 5MB까지", example = "1048576")
    @NotNull @Positive Long fileSize
) {
}
