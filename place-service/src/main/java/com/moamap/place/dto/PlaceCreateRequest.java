package com.moamap.place.dto;

import java.math.BigDecimal;
import com.moamap.place.entity.PlaceSourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PlaceCreateRequest(
    @NotBlank String name,
    String address,
    String roadAddress,
    @NotNull BigDecimal lat,
    @NotNull BigDecimal lng,
    String category,
    @NotBlank String kakaoPlaceId,
    @NotNull PlaceSourceType sourceType,
    String sourceUrl,
    String description,
    @NotNull Long mapId
) {
}
