package com.moamap.place.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CommentPhotoUploadUrlRequest(
    @NotBlank String contentType,
    @NotNull @Positive Long fileSize
) {
}
