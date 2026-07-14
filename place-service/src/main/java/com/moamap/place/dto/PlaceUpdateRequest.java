package com.moamap.place.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.NotBlank;

public record PlaceUpdateRequest(
    @NotBlank String name,
    String address,
    String roadAddress,
    BigDecimal lat,
    BigDecimal lng,
    String category,
    String description
) {
}
