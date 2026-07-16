package com.moamap.place.dto;

import java.math.BigDecimal;

/**
 * 부분 수정(PATCH) 요청. 필드를 안 보내면(null) 기존 값을 유지하고, 값을 보낸 필드만 바꾼다.
 */
public record PlaceUpdateRequest(
    String name,
    String address,
    String roadAddress,
    BigDecimal lat,
    BigDecimal lng,
    String category,
    String description
) {
}
