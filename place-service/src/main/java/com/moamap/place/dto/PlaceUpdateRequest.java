package com.moamap.place.dto;

import java.math.BigDecimal;
import java.util.List;
import jakarta.validation.constraints.Size;

/**
 * 부분 수정(PATCH) 요청. 필드를 안 보내면(null) 기존 값을 유지하고, 값을 보낸 필드만 바꾼다.
 * tags는 보내면 기존 목록을 통째로 교체한다(부분 추가/삭제가 아님).
 */
public record PlaceUpdateRequest(
    String name,
    String address,
    String roadAddress,
    BigDecimal lat,
    BigDecimal lng,
    String category,
    String description,
    List<@Size(max = 30) String> tags
) {
}
