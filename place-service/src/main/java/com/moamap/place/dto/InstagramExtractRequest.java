package com.moamap.place.dto;

import jakarta.validation.constraints.NotBlank;

public record InstagramExtractRequest(
    @NotBlank(message = "url은 필수입니다.") String url,
    @NotBlank(message = "description은 필수입니다.") String description
) {
}
