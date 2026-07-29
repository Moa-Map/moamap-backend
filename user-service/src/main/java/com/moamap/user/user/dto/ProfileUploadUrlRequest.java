package com.moamap.user.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ProfileUploadUrlRequest(
    @NotBlank String contentType,
    @NotNull @Positive Long fileSize
) {
}
