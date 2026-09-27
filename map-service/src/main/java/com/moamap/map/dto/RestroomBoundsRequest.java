package com.moamap.map.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record RestroomBoundsRequest(
    @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal swLat,
    @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal swLng,
    @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal neLat,
    @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal neLng
) {
}
