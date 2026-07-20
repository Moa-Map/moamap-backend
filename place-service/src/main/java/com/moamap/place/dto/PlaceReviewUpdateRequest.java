package com.moamap.place.dto;

import java.util.List;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record PlaceReviewUpdateRequest(
    @Min(value = 1, message = "rating은 1 이상이어야 합니다.")
    @Max(value = 5, message = "rating은 5 이하여야 합니다.")
    Integer rating,
    String content,
    List<String> imageUrls
) {
}
