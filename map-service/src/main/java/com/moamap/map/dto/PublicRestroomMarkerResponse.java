package com.moamap.map.dto;

import java.math.BigDecimal;

import com.moamap.map.entity.PublicRestroom;

public record PublicRestroomMarkerResponse(
    Long id,
    String name,
    BigDecimal lat,
    BigDecimal lng,
    String category,
    boolean disabledAccessible,
    String openHours
) {

    public static PublicRestroomMarkerResponse from(PublicRestroom r) {
        boolean disabledAccessible = r.getMaleDisabledToilet() > 0
            || r.getMaleDisabledUrinal() > 0
            || r.getFemaleDisabledToilet() > 0;
        return new PublicRestroomMarkerResponse(
            r.getId(),
            r.getName(),
            r.getLat(),
            r.getLng(),
            r.getCategory(),
            disabledAccessible,
            r.getOpenHours()
        );
    }
}
